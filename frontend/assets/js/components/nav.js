/* =====================================================================
   Gezinme.

   Masaustunde yatay menu, dar ekranda hamburger. Menu icerigi tek
   yerde tanimlidir; her sayfa ayni menuyu alir ve bulundugu sayfa
   aria-current ile isaretlenir.
   ===================================================================== */

const PUBLIC_LINKS = [
    { href: "/index.html", label: "Ana sayfa" },
    { href: "/nasil-calisir.html", label: "Nasıl çalışır" },
];

const APP_LINKS = [
    { href: "/app/bugun.html", label: "Bugün" },
    { href: "/app/program.html", label: "Program" },
    { href: "/app/kelimeler.html", label: "Kelimeler" },
    { href: "/app/testler.html", label: "Testler" },
    { href: "/app/gelisim.html", label: "Gelişim" },
];

/**
 * @param {{ loggedIn?: boolean, userName?: string }} options
 */
export function mountNav({ loggedIn = false, userName = "" } = {}) {
    const host = document.querySelector("[data-nav]");
    if (!host) return;

    const links = loggedIn ? APP_LINKS : PUBLIC_LINKS;
    const here = window.location.pathname;
    const isCurrent = (href) => here === href || here.endsWith(href);

    host.innerHTML = `
        <a class="skip-link" href="#main">İçeriğe geç</a>
        <header class="topbar${loggedIn ? " topbar--app" : ""}">
            <div class="wrap topbar__inner">
                <a class="brand" href="${loggedIn ? "/app/bugun.html" : "/index.html"}">
                    <span class="brand__mark" aria-hidden="true">ID</span>
                    <span>Ich spreche Deutsch</span>
                </a>

                <nav class="nav" aria-label="Ana menü">
                    ${links.map((l) => `
                        <a class="nav__link${isCurrent(l.href) ? " nav__link--current" : ""}"
                           href="${l.href}"
                           ${isCurrent(l.href) ? 'aria-current="page"' : ""}>${l.label}</a>`).join("")}
                </nav>

                <div class="topbar__actions">
                    ${loggedIn ? `
                        <a class="nav__link" href="/app/ayarlar.html">Ayarlar</a>
                        <span class="user-chip">
                            <span class="user-chip__avatar" aria-hidden="true">${bashHarf(userName)}</span>
                            ${esc(userName || "Hesabım")}
                        </span>
                        <button class="btn btn--quiet" type="button" data-logout>Çıkış</button>
                    ` : `
                        <a class="btn btn--ghost" href="/giris.html">Giriş yap</a>
                        <a class="btn btn--primary" href="/kayit.html">Ücretsiz başla</a>
                    `}
                </div>

                <button class="nav-toggle" type="button"
                        aria-expanded="false" aria-controls="nav-panel">
                    <span class="visually-hidden">Menüyü aç</span>
                    <span class="nav-toggle__bars" aria-hidden="true"><span></span></span>
                </button>
            </div>

            <nav class="nav-panel" id="nav-panel" aria-label="Menü" hidden>
                <div class="wrap">
                    <ul class="nav-panel__list">
                        ${links.map((l) => `
                            <li><a class="nav-panel__link${isCurrent(l.href) ? " nav-panel__link--current" : ""}"
                                   href="${l.href}">${l.label}</a></li>`).join("")}
                        ${loggedIn
                            ? `<li><a class="nav-panel__link" href="/app/ayarlar.html">Ayarlar</a></li>`
                            : ""}
                    </ul>
                    <div class="nav-panel__actions">
                        ${loggedIn
                            ? `<button class="btn btn--ghost btn--block" type="button" data-logout>Çıkış yap</button>`
                            : `<a class="btn btn--ghost btn--block" href="/giris.html">Giriş yap</a>
                               <a class="btn btn--primary btn--block" href="/kayit.html">Ücretsiz başla</a>`}
                    </div>
                </div>
            </nav>
        </header>`;

    bagla(host);
}

function bagla(host) {
    const toggle = host.querySelector(".nav-toggle");
    const panel = host.querySelector(".nav-panel");
    const label = toggle.querySelector(".visually-hidden");

    const setOpen = (open) => {
        toggle.setAttribute("aria-expanded", String(open));
        panel.hidden = !open;
        label.textContent = open ? "Menüyü kapat" : "Menüyü aç";
    };

    toggle.addEventListener("click", () => {
        setOpen(toggle.getAttribute("aria-expanded") !== "true");
    });

    document.addEventListener("keydown", (e) => {
        if (e.key === "Escape" && !panel.hidden) {
            setOpen(false);
            toggle.focus();
        }
    });

    document.addEventListener("click", (e) => {
        if (!panel.hidden && !host.contains(e.target)) setOpen(false);
    });

    // Genislik masaustune cikarsa acik panel kapansin.
    window.matchMedia("(min-width: 901px)").addEventListener("change", (e) => {
        if (e.matches) setOpen(false);
    });
}

function bashHarf(ad) {
    return (ad || "?").trim().charAt(0).toLocaleUpperCase("tr-TR");
}

function esc(s) {
    const d = document.createElement("div");
    d.textContent = s ?? "";
    return d.innerHTML;
}
