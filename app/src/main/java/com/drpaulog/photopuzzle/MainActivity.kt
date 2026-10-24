package com.drpaulog.photopuzzle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drpaulog.photopuzzle.ui.GameScreen
import com.drpaulog.photopuzzle.ui.LevelsScreen
import com.drpaulog.photopuzzle.ui.PuzzleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuzzleTheme {
                val vm: PuzzleViewModel = viewModel()
                val game = vm.game
                if (game == null) {
                    LevelsScreen(vm)
                } else {
                    BackHandler { vm.exitGame() }
                    GameScreen(vm, game)
                }
            }
        }
    }
}
