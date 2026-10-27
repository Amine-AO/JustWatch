package com.justwatch.links

import org.junit.Assert.*
import org.junit.Test

class DomainMatchTest {
    @Test
    fun wildcardMatchesOnlySubdomainsAtLabelBoundary() {
        assertTrue(DomainMatch.matches("www.instagram.com", "*.instagram.com"))
        assertFalse(DomainMatch.matches("instagram.com", "*.instagram.com"))
        assertFalse(DomainMatch.matches("evilinstagram.com", "*.instagram.com"))
        assertFalse(DomainMatch.matches("instagram.com.evil.example", "*.instagram.com"))
    }
}
