package com.example.autocall

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        CallScheduler.scheduleNext(ctx)

        val number = CallScheduler.number(ctx)
        if (number.isBlank()) return
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return

        val telecom = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        CallScheduler.scheduleNext(ctx)
    }
}
