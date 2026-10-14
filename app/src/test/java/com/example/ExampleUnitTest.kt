package com.example

import com.example.data.local.AdminPreferences
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPinDifferentiation_ExitPinAndAdminPinAreDistinct() {
    assertEquals("1ctAdm1n!", AdminPreferences.DEFAULT_ADMIN_PIN)
    assertEquals("R4hasi@", AdminPreferences.DEFAULT_EXIT_PIN)
    assertNotEquals(AdminPreferences.DEFAULT_ADMIN_PIN, AdminPreferences.DEFAULT_EXIT_PIN)
  }
}
