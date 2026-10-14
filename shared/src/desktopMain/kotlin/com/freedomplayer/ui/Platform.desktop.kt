package com.freedomplayer.ui

import java.time.LocalTime
import java.util.TimeZone

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun currentHour(): Int = LocalTime.now().hour

actual fun utcOffsetMillis(atMillis: Long): Long = TimeZone.getDefault().getOffset(atMillis).toLong()
