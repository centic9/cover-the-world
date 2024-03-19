package org.dstadler.ctw.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EmptyStackException;

import org.junit.jupiter.api.Test;

public class IntStackTest {
    @Test
    void testEmpty() {
        IntStack stack = new IntStack();
        assertTrue(stack.isEmpty());
        assertThrows(EmptyStackException.class,
				stack::peek);
        assertThrows(EmptyStackException.class,
				stack::pop);
        assertEquals(0, stack.size());
        assertEquals(10, stack.getCapacity());
        stack.trimToSize();
        assertEquals(0, stack.getCapacity());
    }

    @Test
    void test() {
        IntStack stack = new IntStack();
        stack.push(9);

        assertFalse(stack.isEmpty());
        assertEquals(9, stack.peek());
        assertEquals(1, stack.size());
        assertEquals(10, stack.getCapacity());
		stack.trimToSize();
		assertEquals(1, stack.getCapacity());

        assertEquals(9, stack.pop());

        assertTrue(stack.isEmpty());
        assertThrows(EmptyStackException.class,
                stack::peek);
        assertThrows(EmptyStackException.class,
                stack::pop);
        assertEquals(0, stack.size());
        assertEquals(1, stack.getCapacity());
        stack.trimToSize();
        assertEquals(0, stack.getCapacity());
    }

	@Test
	void testMany() {
		IntStack stack = new IntStack();
		for (int i = 0; i < 10_000; i++) {
			stack.push(i);

			assertFalse(stack.isEmpty());
			assertEquals(i, stack.peek());
			assertEquals(i + 1, stack.size());
			assertTrue(stack.getCapacity() >= i);
		}

		for (int i = 0; i < 10_000; i++) {
			assertFalse(stack.isEmpty());
			assertEquals(10_000 - i - 1, stack.peek());
			assertEquals(10_000 - i, stack.size());
			assertTrue(stack.getCapacity() >= i);

			assertEquals(10_000 - i - 1, stack.pop());
		}

		assertTrue(stack.isEmpty());
		assertThrows(EmptyStackException.class,
				stack::peek);
		assertThrows(EmptyStackException.class,
				stack::pop);
		assertEquals(0, stack.size());
		assertTrue(stack.getCapacity() > 0);
		stack.trimToSize();
		assertEquals(0, stack.getCapacity());
	}

    @Test
    void testInitial() {
        IntStack stack = new IntStack(123);
        assertTrue(stack.isEmpty());
        assertThrows(EmptyStackException.class,
                stack::peek);
        assertThrows(EmptyStackException.class,
                stack::pop);
        assertEquals(0, stack.size());
        assertEquals(123, stack.getCapacity());
        stack.trimToSize();
        assertEquals(0, stack.getCapacity());
    }
}
