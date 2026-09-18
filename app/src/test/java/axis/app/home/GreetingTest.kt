package axis.app.home

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {

    @Test
    fun dayparts_mapCorrectly() {
        assertEquals("Good morning", greetingFor(5, null))
        assertEquals("Good morning", greetingFor(11, null))
        assertEquals("Good afternoon", greetingFor(12, null))
        assertEquals("Good afternoon", greetingFor(16, null))
        assertEquals("Good evening", greetingFor(17, null))
        assertEquals("Good evening", greetingFor(21, null))
        assertEquals("Good night", greetingFor(22, null))
        assertEquals("Good night", greetingFor(4, null))
    }

    @Test
    fun name_appendsWhenPresent() {
        assertEquals("Good evening, Ada", greetingFor(19, "Ada"))
        assertEquals("Good evening", greetingFor(19, null))
        assertEquals("Good evening", greetingFor(19, "  "))
    }
}
