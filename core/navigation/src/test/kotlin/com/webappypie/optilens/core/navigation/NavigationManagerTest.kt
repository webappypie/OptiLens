package com.webappypie.optilens.core.navigation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationManagerTest {

    private val navManager = NavigationManagerImpl()

    @Test
    fun `navigate emits NavigateTo command with destination`() = runTest {
        navManager.navigate(AppDestination.Settings)
        val command = navManager.navigationCommands.first()

        assertTrue(command is NavigationCommand.NavigateTo)
        val navigateTo = command as NavigationCommand.NavigateTo
        assertEquals(AppDestination.Settings, navigateTo.destination)
    }

    @Test
    fun `navigateBack emits NavigateBack command`() = runTest {
        navManager.navigateBack()
        val command = navManager.navigationCommands.first()

        assertEquals(NavigationCommand.NavigateBack, command)
    }
}
