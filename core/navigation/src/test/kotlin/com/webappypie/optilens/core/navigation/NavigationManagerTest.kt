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
    fun `navigate to AiTools emits NavigateTo command`() = runTest {
        navManager.navigate(AppDestination.AiTools)
        val command = navManager.navigationCommands.first()

        assertTrue(command is NavigationCommand.NavigateTo)
        val navigateTo = command as NavigationCommand.NavigateTo
        assertEquals(AppDestination.AiTools, navigateTo.destination)
    }

    @Test
    fun `navigate to Gallery emits NavigateTo command`() = runTest {
        navManager.navigate(AppDestination.Gallery)
        val command = navManager.navigationCommands.first()

        assertTrue(command is NavigationCommand.NavigateTo)
        val navigateTo = command as NavigationCommand.NavigateTo
        assertEquals(AppDestination.Gallery, navigateTo.destination)
    }

    @Test
    fun `navigate to ProUpgrade emits NavigateTo command`() = runTest {
        navManager.navigate(AppDestination.ProUpgrade)
        val command = navManager.navigationCommands.first()

        assertTrue(command is NavigationCommand.NavigateTo)
        val navigateTo = command as NavigationCommand.NavigateTo
        assertEquals(AppDestination.ProUpgrade, navigateTo.destination)
    }

    @Test
    fun `navigateBack emits NavigateBack command`() = runTest {
        navManager.navigateBack()
        val command = navManager.navigationCommands.first()

        assertEquals(NavigationCommand.NavigateBack, command)
    }
}
