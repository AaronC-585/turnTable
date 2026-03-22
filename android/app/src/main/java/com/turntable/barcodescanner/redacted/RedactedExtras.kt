package com.turntable.barcodescanner.redacted

object RedactedExtras {
    const val GROUP_ID = "group_id"
    /** Prefill collage form: comma-separated group IDs (e.g. single group). */
    const val GROUP_IDS_CSV = "group_ids_csv"
    const val ARTIST_ID = "artist_id"
    const val TORRENT_ID = "torrent_id"
    const val USER_ID = "user_id"
    const val INITIAL_QUERY = "initial_query"
    /** Prefill [RedactedBrowseActivity] advanced fields (e.g. artist page file-list search). */
    const val BROWSE_ADVANCED_ARTIST_NAME = "browse_advanced_artist_name"
    const val BROWSE_ADVANCED_FILELIST = "browse_advanced_filelist"
    /** When true with artist/filelist extras, open [RedactedBrowseResultsActivity] after prefill. Default true. */
    const val BROWSE_AUTO_SUBMIT_RESULTS = "browse_auto_submit_results"
    /** JSON object of string keys/values for `browse` API (see [RedactedBrowseParamsCodec]). */
    const val BROWSE_PARAMS_JSON = "browse_params_json"
    const val REQUEST_ID = "request_id"
    const val CONV_ID = "conv_id"
    const val FORUM_ID = "forum_id"
    const val FORUM_NAME = "forum_name"
    const val THREAD_ID = "thread_id"
    const val COLLAGE_ID = "collage_id"
}
