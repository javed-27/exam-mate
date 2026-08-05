package com.exammate.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.exammate.ui.home.HomeScreen
import com.exammate.ui.mcq.McqSolverScreen
import com.exammate.ui.splash.SplashScreen
import com.exammate.ui.theory.TheorySolverScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val MCQ_SOLVER = "mcq_solver"
    const val THEORY_SOLVER = "theory_solver"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onMcqClick = { navController.navigate(Routes.MCQ_SOLVER) },
                onTheoryClick = { navController.navigate(Routes.THEORY_SOLVER) },
            )
        }
        composable(Routes.MCQ_SOLVER) {
            McqSolverScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.THEORY_SOLVER) {
            TheorySolverScreen(onBack = { navController.popBackStack() })
        }
    }
}
