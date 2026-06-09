package com.explorer.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProgrammaticDiagram(type: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "System Schematic: $type",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        when (type) {
            "MEMORY_MAP" -> N64MemoryMapDiagram()
            "ARCH_FLOW" -> SystemArchitectureDiagram()
            else -> Text("Diagram type '$type' not yet modeled.", color = Color.Red)
        }
    }
}

@Composable
fun N64MemoryMapDiagram() {
    // Factual representation of RDRAM layout and physical addressing
    Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // System Bus Line
        drawLine(
            color = Color.Gray,
            start = Offset(canvasWidth / 2, 0f),
            end = Offset(canvasWidth / 2, canvasHeight),
            strokeWidth = 8f
        )
        
        // Kernel Segments (KSEG0/KSEG1)
        drawRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(10f, 20f),
            size = Size(canvasWidth / 2 - 30f, 60f)
        )
        
        // Physical RDRAM Space
        drawRect(
            color = Color(0xFF2196F3),
            topLeft = Offset(canvasWidth / 2 + 20f, 80f),
            size = Size(canvasWidth / 2 - 30f, 100f)
        )
    }
}

@Composable
fun SystemArchitectureDiagram() {
    // Flow representing CPU -> RSP (Reality Signal Processor) -> RDP (Reality Display Processor)
    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val canvasWidth = size.width
        val boxWidth = canvasWidth / 3.5f
        val boxHeight = 80f
        val startY = 50f
        
        // VR4300 CPU
        drawRect(Color(0xFFE91E63), Offset(10f, startY), Size(boxWidth, boxHeight))
        
        // RSP
        drawRect(Color(0xFFFF9800), Offset(boxWidth + 30f, startY), Size(boxWidth, boxHeight))
        
        // RDP
        drawRect(Color(0xFF9C27B0), Offset((boxWidth * 2) + 50f, startY), Size(boxWidth, boxHeight))
        
        // Connecting arrows
        drawLine(
            Color.White, 
            Offset(boxWidth + 10f, startY + boxHeight / 2), 
            Offset(boxWidth + 30f, startY + boxHeight / 2), 
            strokeWidth = 6f
        )
        drawLine(
            Color.White, 
            Offset((boxWidth * 2) + 30f, startY + boxHeight / 2), 
            Offset((boxWidth * 2) + 50f, startY + boxHeight / 2), 
            strokeWidth = 6f
        )
    }
}
