package ir.patraplus.webui

class JavaScriptInjector {
    private val scripts = linkedMapOf<String, String>()

    init {
        registerScript("Extract Records", MAIN_EXTRACTION_SCRIPT)
    }

    fun registerScript(name: String, script: String) {
        scripts[name] = script
    }

    fun unregisterScript(name: String) {
        scripts.remove(name)
    }

    fun availableScripts(): List<String> = scripts.keys.toList()

    fun inject(name: String, onResult: ((String?) -> Unit)? = null) {
        val script = scripts[name] ?: return
        WebViewHolder.getWebView()?.evaluateJavascript(script) { result ->
            onResult?.invoke(result)
        }
    }

    companion object {
        private const val MAIN_EXTRACTION_SCRIPT = """
(() => {
    /* --------------------------------------------------
       1) استخراج تاریخ ثبت فقط از جدول search_writer
    -------------------------------------------------- */
    function pickRegistrationDate(anchor) {
        const row = anchor.closest("tr");
        if (!row) return "";

        const table = row.closest("table");
        if (!table) return "";

        const rows = [...table.querySelectorAll("tr")];

        // پیدا کردن ردیف هدر واقعی (اولین tr که th دارد)
        const headerRow = rows.find(r => r.querySelectorAll("th").length > 0);
        if (!headerRow) return "";

        const headers = [...headerRow.querySelectorAll("th")]
            .map(th => th.textContent.replace(/\s+/g, "").trim());

        // پیدا کردن ستون «تاریخ ثبت»
        const dateColIndex = headers.findIndex(h => /تاریخثبت/i.test(h));
        if (dateColIndex === -1) return "";

        const cells = [...row.querySelectorAll("td")];
        if (!cells[dateColIndex]) return "";

        return cells[dateColIndex].textContent.trim();
    }

    function pickStatus(anchor) {
        const row = anchor.closest("tr");
        if (!row) return "";

        const table = row.closest("table");
        if (!table) return "";

        const rows = [...table.querySelectorAll("tr")];
        const headerRow = rows.find(r => r.querySelectorAll("th").length > 0);
        if (!headerRow) return "";

        const headers = [...headerRow.querySelectorAll("th")]
            .map(th => th.textContent.replace(/\s+/g, "").trim());

        const statusColIndex = headers.findIndex(h => /وضعیتمشتری|وضعیت/i.test(h));
        if (statusColIndex === -1) return "";

        const cells = [...row.querySelectorAll("td")];
        if (!cells[statusColIndex]) return "";

        return cells[statusColIndex].textContent.trim();
    }

    /* --------------------------------------------------
       2) جمع‌آوری لینک‌ها + تاریخ ثبت (فقط یک‌بار)
    -------------------------------------------------- */
    if (!window._patraLinks) {
        const anchors = [
            ...document.querySelectorAll("a[href*='view_search_writer']")
        ];

        const collected = anchors.map(a => ({
            url: a.href,
            registeredAt: pickRegistrationDate(a),
            status: pickStatus(a)
        }));

        const unique = new Map();
        for (const { url, registeredAt, status } of collected) {
            if (!unique.has(url)) {
                unique.set(url, { registeredAt: registeredAt || "", status: status || "" });
            } else {
                const current = unique.get(url);
                unique.set(url, {
                    registeredAt: current.registeredAt || registeredAt || "",
                    status: current.status || status || ""
                });
            }
        }

        window._patraLinks = [...unique.entries()].map(
            ([url, payload]) => ({ url, registeredAt: payload.registeredAt, status: payload.status })
        );
    }

    const links = window._patraLinks;
    if (!links.length) {
        return JSON.stringify({ error: "هیچ لینکی پیدا نشد." });
    }

    /* --------------------------------------------------
       3) جلوگیری از رکورد تکراری
    -------------------------------------------------- */
    const seen = window._patraSeen || (window._patraSeen = new Set());

    /* --------------------------------------------------
       4) استخراج اطلاعات صفحه view_search_writer
    -------------------------------------------------- */
    function extract(link) {
        const { url, registeredAt, status: listStatus } = link;

        try {
            const request = new XMLHttpRequest();
            request.open("GET", url, false);
            request.withCredentials = true;
            request.send(null);

            if (request.status < 200 || request.status >= 300) {
                return null;
            }

            const html = request.responseText;
            const doc = new DOMParser().parseFromString(html, "text/html");

            const labelMap = new Map();
            const tds = [...doc.querySelectorAll("td")];

            for (let i = 0; i < tds.length - 1; i++) {
                const key = tds[i].textContent.trim();
                if (key && !labelMap.has(key)) {
                    labelMap.set(key, tds[i + 1].textContent.trim());
                }
            }

            const td = label => labelMap.get(label) || "";

            function normalizeStatus(raw = "") {
                return raw
                    .replace(/[ي]/g, "ی")
                    .replace(/[ك]/g, "ک")
                    .replace(/\s+/g, " ")
                    .trim();
            }

            function mapStatus(rawStatus) {
                const s = normalizeStatus(rawStatus);
                if (!s) return "";
                if (/در\s*انتظار.*تحویل/.test(s)) return "در انتظار تحویل";
                if (/وصولی/.test(s)) return "وصولی";
                if (/کنسل\s*نهایی|کنسلی/.test(s)) return "کنسل نهایی";
                if (/انصرافی\s*هماهنگی|انصرافی/.test(s)) return "انصرافی هماهنگی";
                return s;
            }

            const rawStatus =
                td("وضعیت") ||
                td("وضعیت پرونده") ||
                td("وضعیت سفارش");

            const finalStatus = mapStatus(rawStatus || listStatus) || normalizeStatus(listStatus);

            return {
                نام: td("نام و نام خانوادگی"),
                "شماره موبایل": td("شماره موبایل"),
                "شماره تلفن": td("شماره تلفن"),
                استان: td("استان"),
                شهر: td("شهر"),
                "کد ارسال": td("کد ارسال"),
                آدرس: td("آدرس"),
                توضیحات: td("توضیحات"),
                "تاریخ ثبت": registeredAt || "",
                فروشنده: td("فروشنده"),
                وضعیت: finalStatus
            };
        } catch (err) {
            return null;
        }
    }

    const raw = links.map(extract);
    const clean = raw.filter(Boolean);

    /* --------------------------------------------------
       5) حذف تکراری‌ها (نام + موبایل)
    -------------------------------------------------- */
    const finalRows = [];
    for (const r of clean) {
        const key = (r["نام"] + r["شماره موبایل"]).replace(/\s+/g, "");
        if (key && !seen.has(key)) {
            seen.add(key);
            finalRows.push(r);
        }
    }

    if (!finalRows.length) {
        return JSON.stringify({ error: "هیچ رکورد جدیدی باقی نماند." });
    }

    return JSON.stringify(finalRows);
})();
"""
    }
}
