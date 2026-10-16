package com.lore.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StopCommandTest {

    @Test
    void plural_uses_singular_only_for_one() {
        assertEquals("0 files", StopCommand.plural(0, "file"));
        assertEquals("1 file", StopCommand.plural(1, "file"));
        assertEquals("2 files", StopCommand.plural(2, "file"));
        assertEquals("1 change", StopCommand.plural(1, "change"));
    }
}
