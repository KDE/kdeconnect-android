package org.kde.kdeconnect.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

data class CreateFileParams(
    val fileMimeType: String,
    val suggestedFileName: String,
)

class CreateFileResultContract : ActivityResultContract<CreateFileParams, Uri?>() {

    override fun createIntent(context: Context, input: CreateFileParams): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setTypeAndNormalize(input.fileMimeType)
            putExtra(Intent.EXTRA_TITLE, input.suggestedFileName)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = when (resultCode) {
        Activity.RESULT_OK -> intent?.data
        else -> null
    }
}
