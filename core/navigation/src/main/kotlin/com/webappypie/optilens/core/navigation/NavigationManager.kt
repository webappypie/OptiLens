package com.webappypie.optilens.core.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Commands for navigating across screens.
 */
sealed interface NavigationCommand {
    data class NavigateTo(
        val destination: AppDestination,
        val popUpTo: AppDestination? = null,
        val inclusive: Boolean = false,
        val singleTop: Boolean = true,
    ) : NavigationCommand

    data object NavigateBack : NavigationCommand
}

/**
 * Interface allowing ViewModels and domain components to emit navigation events
 * in an architecture-compliant, decoupled manner.
 */
interface NavigationManager {
    val navigationCommands: Flow<NavigationCommand>

    suspend fun navigate(
        destination: AppDestination,
        popUpTo: AppDestination? = null,
        inclusive: Boolean = false,
        singleTop: Boolean = true,
    )

    suspend fun navigateBack()
}

/**
 * Production implementation of [NavigationManager].
 */
@Singleton
class NavigationManagerImpl @Inject constructor() : NavigationManager {

    private val _commands = Channel<NavigationCommand>(capacity = Channel.BUFFERED)
    override val navigationCommands: Flow<NavigationCommand> = _commands.receiveAsFlow()

    override suspend fun navigate(
        destination: AppDestination,
        popUpTo: AppDestination?,
        inclusive: Boolean,
        singleTop: Boolean,
    ) {
        _commands.send(
            NavigationCommand.NavigateTo(
                destination = destination,
                popUpTo = popUpTo,
                inclusive = inclusive,
                singleTop = singleTop,
            )
        )
    }

    override suspend fun navigateBack() {
        _commands.send(NavigationCommand.NavigateBack)
    }
}
