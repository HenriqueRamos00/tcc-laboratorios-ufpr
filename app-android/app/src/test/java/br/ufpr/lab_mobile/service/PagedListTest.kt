package br.ufpr.lab_mobile.service

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PagedListTest {
    @Test
    fun `every page is delivered as soon as it arrives`() = runBlocking {
        val list = PagedList(2) { page -> PageResult(listOf("$page-a", "$page-b").take(if (page == 3) 1 else 2), total = 5) }
        val seen = mutableListOf<List<String>>()
        val progress = mutableListOf<Pair<Int, Int?>>()

        list.loadRemaining {
            seen += list.items
            progress += list.covered to list.total
        }

        assertEquals(listOf(
            listOf("1-a", "1-b"),
            listOf("1-a", "1-b", "2-a", "2-b"),
            listOf("1-a", "1-b", "2-a", "2-b", "3-a"),
        ), seen)
        assertEquals(listOf(2 to 5, 4 to 5, 5 to 5), progress)
        assertTrue(list.isComplete)
    }

    @Test
    fun `a failed page keeps what arrived and the next call resumes from it`() = runBlocking {
        val failure = IOException("timeout")
        val pages = mutableListOf<Int>()
        var offline = true
        val list = PagedList(2) { page ->
            pages += page
            if (page == 2 && offline) throw failure
            PageResult(listOf("$page-a", "$page-b"), total = 6)
        }

        try {
            list.loadRemaining()
            fail("Expected the original failure")
        } catch (error: IOException) {
            assertSame(failure, error)
        }
        assertEquals(listOf("1-a", "1-b"), list.items)
        assertEquals(2, list.covered)
        assertFalse(list.isComplete)

        offline = false
        list.loadRemaining()

        assertEquals(listOf(1, 2, 2, 3), pages)
        assertEquals(listOf("1-a", "1-b", "2-a", "2-b", "3-a", "3-b"), list.items)
        assertTrue(list.isComplete)
    }

    @Test
    fun `cancellation also leaves the list ready to resume`() = runBlocking {
        var cancelled = true
        val list = PagedList(1) { page ->
            if (page == 2 && cancelled) throw CancellationException("left the screen")
            PageResult(listOf(page), total = 2)
        }
        try {
            list.loadRemaining()
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        cancelled = false
        list.loadRemaining()
        assertEquals(listOf(1, 2), list.items)
    }

    @Test
    fun `a page whose items the API withheld does not end the list`() = runBlocking {
        val list = PagedList(2) { page -> PageResult(if (page == 2) emptyList() else listOf("$page"), total = 6) }
        var calls = 0
        list.loadRemaining { calls++ }
        assertEquals(3, calls)
        assertEquals(listOf("1", "3"), list.items)
        assertEquals("the progress counts what was read, not what is shown", 6, list.covered)
    }

    @Test
    fun `a complete list is not requested again`() = runBlocking {
        var calls = 0
        val list = PagedList(20) { calls++; PageResult(listOf("only"), total = null) }
        list.loadRemaining()
        list.loadRemaining()
        assertEquals(1, calls)
        assertEquals(1, list.covered)
    }

    @Test
    fun `an empty list is complete after one page`() = runBlocking {
        listOf(0, null).forEach { total ->
            val list = PagedList(20) { PageResult(emptyList<String>(), total) }
            list.loadRemaining()
            assertTrue(list.isComplete)
            assertEquals(0, list.covered)
        }
    }
}
