(() => {
  const STORAGE_LANG_KEY = "ridelink_lang";
  const STORAGE_THEME_KEY = "ridelink_theme";
  const i18n = window.RIDELINK_I18N;
  const fallback = i18n.translations.en;

  const langSelect = document.getElementById("lang-select");
  const langsGrid = document.getElementById("languages-grid");
  const themeToggle = document.getElementById("theme-toggle");
  const systemIcon = document.getElementById("theme-icon-system");
  const sunIcon = document.getElementById("theme-icon-sun");
  const moonIcon = document.getElementById("theme-icon-moon");
  const heroPreviewImg = document.getElementById("hero-preview-img");
  const tabButtons = document.querySelectorAll(".tab-btn[data-screen]");
  const screenItems = document.querySelectorAll(".screen-item[data-screen]");

  const THEME_MODES = ["system", "light", "dark"];
  let currentThemeMode = "system";

  let releaseMeta = {
    version: "1.1.0",
    tag: "v1.1.0",
    apkSizeMb: "5.5 MB",
    releaseUrl: "https://github.com/aiyu-ayaan/RideLink/releases/tag/v1.1.0",
    downloadUrl: "https://github.com/aiyu-ayaan/RideLink/releases/latest/download/RideLink.apk"
  };
  let currentLang = "en";

  function formatWithRelease(str) {
    return str
      .replaceAll("v1.1.0", releaseMeta.tag)
      .replaceAll("1.1.0", releaseMeta.version)
      .replaceAll("5.5 MB", releaseMeta.apkSizeMb);
  }

  function applyReleaseLinks() {
    const cookieLabel = document.querySelector(".hero-cookie-accent span");
    if (cookieLabel) cookieLabel.textContent = releaseMeta.tag;
    document.querySelectorAll('a[href*="RideLink.apk"]').forEach((a) => {
      a.href = releaseMeta.downloadUrl;
    });
  }

  async function loadVersionMeta() {
    try {
      const resp = await fetch("version.json", { cache: "no-store" });
      if (!resp.ok) return;
      const data = await resp.json();
      if (data && data.version) {
        releaseMeta = { ...releaseMeta, ...data };
        applyReleaseLinks();
        applyLanguage(currentLang, false);
      }
    } catch (_) {}
  }

  function findLang(tag) {
    if (!tag) return null;
    const clean = tag.trim().toLowerCase();
    return (
      i18n.languages.find((l) => l.tag.toLowerCase() === clean) ||
      i18n.languages.find((l) => clean.startsWith(l.tag.toLowerCase() + "-")) ||
      null
    );
  }

  function resolveInitialLang() {
    const params = new URLSearchParams(window.location.search);
    const fromUrl = findLang(params.get("lang"));
    if (fromUrl) return fromUrl.tag;

    try {
      const fromStorage = findLang(localStorage.getItem(STORAGE_LANG_KEY));
      if (fromStorage) return fromStorage.tag;
    } catch (_) {}

    const fromNav = findLang(navigator.language);
    return fromNav ? fromNav.tag : "en";
  }

  function applyLanguage(tag, persist = true) {
    const langObj = findLang(tag) || i18n.languages[0];
    const activeTag = langObj.tag;
    currentLang = activeTag;
    const dict = i18n.translations[activeTag] || fallback;

    document.documentElement.lang = activeTag;
    document.documentElement.dir = langObj.rtl ? "rtl" : "ltr";

    document.querySelectorAll("[data-i18n]").forEach((el) => {
      const key = el.getAttribute("data-i18n");
      const text = dict[key] || fallback[key];
      if (text) {
        el.textContent = formatWithRelease(text);
      }
    });

    if (langSelect) {
      langSelect.value = activeTag;
    }

    if (langsGrid) {
      langsGrid.querySelectorAll(".lang-chip").forEach((chip) => {
        chip.setAttribute("aria-pressed", chip.dataset.lang === activeTag ? "true" : "false");
      });
    }

    if (persist) {
      try {
        localStorage.setItem(STORAGE_LANG_KEY, activeTag);
      } catch (_) {}
      const url = new URL(window.location.href);
      if (activeTag === "en") {
        url.searchParams.delete("lang");
      } else {
        url.searchParams.set("lang", activeTag);
      }
      window.history.replaceState({}, "", url.toString());
    }
  }

  function populateLanguages() {
    if (langSelect) {
      i18n.languages.forEach((lang) => {
        const opt = document.createElement("option");
        opt.value = lang.tag;
        opt.textContent = lang.nativeName;
        langSelect.appendChild(opt);
      });
      langSelect.addEventListener("change", (e) => {
        applyLanguage(e.target.value, true);
      });
    }

    if (langsGrid) {
      i18n.languages.forEach((lang) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "lang-chip";
        btn.dataset.lang = lang.tag;
        btn.setAttribute("aria-pressed", "false");
        btn.title = lang.englishName;

        const nameSpan = document.createElement("span");
        nameSpan.textContent = lang.nativeName;
        const codeSpan = document.createElement("span");
        codeSpan.className = "lang-code";
        codeSpan.textContent = lang.tag;

        btn.appendChild(nameSpan);
        btn.appendChild(codeSpan);
        btn.addEventListener("click", () => applyLanguage(lang.tag, true));
        langsGrid.appendChild(btn);
      });
    }
  }

  function resolveInitialTheme() {
    try {
      const saved = localStorage.getItem(STORAGE_THEME_KEY);
      if (THEME_MODES.includes(saved)) return saved;
    } catch (_) {}
    return "system";
  }

  function isSystemDark() {
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
  }

  function applyTheme(mode, persist = false) {
    currentThemeMode = THEME_MODES.includes(mode) ? mode : "system";
    if (currentThemeMode === "system") {
      document.documentElement.removeAttribute("data-theme");
    } else {
      document.documentElement.setAttribute("data-theme", currentThemeMode);
    }

    const effectiveDark = currentThemeMode === "dark" || (currentThemeMode === "system" && isSystemDark());
    if (systemIcon) systemIcon.toggleAttribute("hidden", currentThemeMode !== "system");
    if (sunIcon) sunIcon.toggleAttribute("hidden", currentThemeMode !== "light");
    if (moonIcon) moonIcon.toggleAttribute("hidden", currentThemeMode !== "dark");

    if (themeToggle) {
      const label =
        currentThemeMode === "system"
          ? "Theme: System (click for Light)"
          : currentThemeMode === "light"
          ? "Theme: Light (click for Dark)"
          : "Theme: Dark (click for System)";
      themeToggle.setAttribute("aria-label", label);
      themeToggle.setAttribute("title", label);
    }

    if (heroPreviewImg) {
      heroPreviewImg.src = effectiveDark ? "screenshots/ride-dark.png" : "screenshots/ride-light.png";
    }
    if (persist) {
      try {
        localStorage.setItem(STORAGE_THEME_KEY, currentThemeMode);
      } catch (_) {}
    }
  }

  function setActiveScreen(screenId) {
    tabButtons.forEach((btn) => {
      btn.setAttribute("aria-selected", btn.dataset.screen === screenId ? "true" : "false");
    });
    screenItems.forEach((item) => {
      item.classList.toggle("is-active", item.dataset.screen === screenId);
    });
  }

  if (themeToggle) {
    themeToggle.addEventListener("click", () => {
      const idx = THEME_MODES.indexOf(currentThemeMode);
      const next = THEME_MODES[(idx + 1) % THEME_MODES.length];
      applyTheme(next, true);
    });
  }

  if (window.matchMedia) {
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
      if (currentThemeMode === "system") applyTheme("system", false);
    });
  }

  tabButtons.forEach((btn) => {
    btn.addEventListener("click", () => setActiveScreen(btn.dataset.screen));
  });

  screenItems.forEach((item) => {
    item.addEventListener("click", () => setActiveScreen(item.dataset.screen));
  });

  populateLanguages();
  applyTheme(resolveInitialTheme(), false);
  applyLanguage(resolveInitialLang(), false);
  loadVersionMeta();
})();
