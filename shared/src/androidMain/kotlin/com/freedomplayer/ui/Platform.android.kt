package com.freedomplayer.ui

import java.time.LocalTime

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun currentHour(): Int = LocalTime.now().hour
