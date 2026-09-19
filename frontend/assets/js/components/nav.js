/* =====================================================================
   Hamburger menu.
   Menu icerigi tek yerde tanimlanir; her sayfa ayni menuyu alir.
   Giris durumuna gore iki farkli menu gosterilir.
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
    { href: "/app/ayarlar.html", label: "Ayarlar" },
];

/**
 * Ust bari ve acilir menuyu sayfaya yerlestirir.
 * @param {{ loggedIn?: boolean }} options
 */
export function mountNav({ loggedIn = false } = {}) {
    const host = document.querySelector("[data-nav]");
    if (!host) return;

    const links = loggedIn ? [...PUBLIC_LINKS, ...APP_LINKS] : PUBLIC_LINKS;
    const current = window.location.pathname;

    host.innerHTML = `
        <header class="topbar">
            <div class="wrap topbar__inner">
                <a class="brand" href="/index.html">
                    Ich spreche <span class="brand__de">Deutsch</span>
                </a>
                <button class="nav-toggle" type="button"
                        aria-expanded="false" aria-controls="nav-panel">
                    <span class="visually-hidden">Menüyü aç</span>
                    <span class="nav-toggle__bar" aria-hidden="true"></span>
                    <span class="nav-toggle__bar" aria-hidden="true"></span>
                    <span class="nav-toggle__bar" aria-hidden="true"></span>
                </button>
            </div>
        </header>
        <nav class="nav-panel" id="nav-panel" hidden>
            <div class="wrap">
                <ul class="nav-panel__list">
                    ${links.map((l) => `
                        <li>
                            <a class="nav-panel__link${
                                current === l.href ? " nav-panel__link--current" : ""
                            }" href="${l.href}">${l.label}</a>
                        </li>`).join("")}
                </ul>
                <div class="nav-panel__actions">
                    ${loggedIn
                        ? `<button class="btn btn--ghost" type="button" data-logout>Çıkış yap</button>`
                        : `<a class="btn btn--ghost" href="/giris.html">Giriş yap</a>
                           <a class="btn btn--primary" href="/kayit.html">Kayıt ol</a>`}
                </div>
            </div>
        </nav>
    `;

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

    // Esc ile kapat, disariya tiklayinca kapat.
    document.addEventListener("keydown", (e) => {
        if (e.key === "Escape" && !panel.hidden) {
            setOpen(false);
            toggle.focus();
        }
    });

    document.addEventListener("click", (e) => {
        if (!panel.hidden && !host.contains(e.target)) setOpen(false);
    });
}
