package com.martige.service

import kotlinx.coroutines.Job

class LightJob {
    companion object {
        var currentLightJob: Job? = null
    }
}
