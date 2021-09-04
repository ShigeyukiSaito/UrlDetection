package com.example.urldetection

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.predictions.aws.AWSPredictionsPlugin
import com.amplifyframework.predictions.models.TextFormatType
import com.amplifyframework.predictions.result.IdentifyTextResult

class MyAmplifyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            // Add these lines to add the AWSCognitoAuthPlugin and AWSPredictionsPlugin plugins
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.addPlugin(AWSPredictionsPlugin())
            Amplify.configure(applicationContext)

            Log.i("MyAmplifyApp", "Initialized Amplify")
        } catch (error: AmplifyException) {
            Log.e("MyAmplifyApp", "Could not initialize Amplify", error)
        }
    }

    fun detectText(image: Bitmap) {
        Amplify.Predictions.identify(
            TextFormatType.PLAIN, image,
            { result ->
                val identifyResult = result as IdentifyTextResult
                Log.i("MyAmplifyApp", "${identifyResult?.fullText}")
            },
            { Log.e("MyAmplifyApp", "Identify text failed", it) }
        )
    }
}