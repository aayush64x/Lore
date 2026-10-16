package com.lore.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class UICommandTest {

    static final String URL = "http://127.0.0.1:8080/?project=%2FUsers%2Fme%2Fapp";

    @Test
    void mac_uses_open() {
        assertArrayEquals(new String[]{"open", URL}, UICommand.browserCommand("Mac OS X", URL));
    }

    @Test
    void windows_uses_rundll32_with_the_url_untouched() {
        assertArrayEquals(new String[]{"rundll32", "url.dll,FileProtocolHandler", URL},
                UICommand.browserCommand("Windows 11", URL));
    }

    @Test
    void linux_and_others_use_xdg_open() {
        assertArrayEquals(new String[]{"xdg-open", URL}, UICommand.browserCommand("Linux", URL));
        assertArrayEquals(new String[]{"xdg-open", URL}, UICommand.browserCommand("FreeBSD", URL));
        assertArrayEquals(new String[]{"xdg-open", URL}, UICommand.browserCommand("", URL));
    }
}
