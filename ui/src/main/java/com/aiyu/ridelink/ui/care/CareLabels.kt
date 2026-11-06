package com.aiyu.ridelink.ui.care

import com.aiyu.ridelink.core.care.CareItem
import com.aiyu.ridelink.ui.R

/** Display name of a care item; also used by the app module's notifications. */
val CareItem.label: Int
    get() = when (this) {
        CareItem.ENGINE_OIL -> R.string.care_engine_oil
        CareItem.AIR_FILTER -> R.string.care_air_filter
        CareItem.BRAKES -> R.string.care_brakes
        CareItem.SPARK_PLUG -> R.string.care_spark_plug
        CareItem.INSURANCE -> R.string.care_insurance
        CareItem.PUC -> R.string.care_puc
    }
