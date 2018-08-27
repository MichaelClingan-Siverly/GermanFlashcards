package mike.networking;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import mike.utils.FileUtils;
import mike.utils.StreamUtils;
import mike.utils.WordPair;

/**
 * Implementation of AsyncTask designed to fetch data from the network.
 */
class DownloadTask extends AsyncTask<String, Void, DownloadTask.Result> {

    private DownloadCallback mCallback;

    DownloadTask(DownloadCallback callback) {
        mCallback = callback;
    }

    /**
     * Wrapper class that serves as a union of a result value and an exception. When the download
     * task has completed, either the result value or exception can be a non-null value.
     * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
     */
    class Result {
        int mResultValue;
        Exception mException;
        Result(int resultValue) {
            mResultValue = resultValue;
        }
        Result(Exception exception) {
            mException = exception;
        }
    }

    /**
     * Cancel background network operation if we do not have network connectivity.
     * Added the ethernet check so that it properly works on my emulator
     */
    @Override
    protected void onPreExecute() {
        if (mCallback != null) {
            NetworkInfo networkInfo = mCallback.getActiveNetworkInfo();
            if (networkInfo == null || !networkInfo.isConnected() ||
                    (networkInfo.getType() != ConnectivityManager.TYPE_WIFI
                            && networkInfo.getType() != ConnectivityManager.TYPE_MOBILE
                            && networkInfo.getType() != ConnectivityManager.TYPE_ETHERNET)) {
                // If no connectivity, cancel task and update Callback with null data.
                mCallback.finishDownloading("No WiFi or network connection", false);
                cancel(true);
            }
        }
    }

    /**
     * Defines work to perform on the background thread.
     */
    @Override
    protected Result doInBackground(String... urls) {
        Result result = null;
        if (!isCancelled() && urls != null && urls.length > 0) {
            String urlString;
            String lastWord = FileUtils.readLastWordFromFile();
            if(lastWord != null)
                urlString = urls[0] + "/?last_word=" + lastWord;
            else
                urlString = urls[0];

            try {
                URL url = new URL(urlString);
                int wordsDownloaded = downloadUrl(url);
                if (wordsDownloaded >= 0) {
                    result = new Result(wordsDownloaded);
                }
                else {
                    result = new Result(new IOException("No response received."));
                }
            }
            catch(Exception e) {
                result = new Result(e);
            }
        }
        return result;
    }

    /**
     * Updates the DownloadCallback with the result so the user can be informed about it.
     */
    @Override
    protected void onPostExecute(Result result) {
        if (result != null && mCallback != null) {
            if (result.mException != null) {
                mCallback.finishDownloading(result.mException.getMessage(), false);
            }
            else if (result.mResultValue >= 0) {
                String value;
                if(result.mResultValue > 0)
                    value = String.valueOf(result.mResultValue);
                else //I just thought "No" would read better than "0"
                    value = "No";
                mCallback.finishDownloading(value, true);
            }
        }
        else if(mCallback != null)
            mCallback.finishDownloading("Something bad happened", false);
    }

    /**
     * Given a URL, sets up a connection and gets the HTTP response body from the server.
     * If the network request is successful, it returns the response body in String form. Otherwise,
     * it will throw an IOException.
     * @param url the URL this will try connecting to
     * @return the number of words read from url
     * @throws IOException something went wrong with the connection or the stream reading from it
     */
    private int downloadUrl(URL url) throws IOException {
        InputStream stream = null;
        HttpURLConnection connection = null;
        int result = -1;
        try {
            connection = makeConnection(url);
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }
            // Retrieve the response body as an InputStream to be processed for word pairs.
            stream = connection.getInputStream();
            if (stream != null) {
                List<WordPair> newPairs = StreamUtils.readStream(stream);
                result = newPairs.size();
                if(result > 0)
                    FileUtils.saveWordsToFile(newPairs);
            }
        }
        finally {
            // Close Stream and disconnect HTTPS connection.
            if (stream != null) {
                stream.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    private HttpURLConnection makeConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        // Timeout for reading InputStream. I'd rather keep the two timeouts under 20 seconds total
        connection.setReadTimeout(5000);
        // Timeout for connection.connect(). figured I'd give a bit of extra time in case of network latency
        connection.setConnectTimeout(15000);
        // For this use case, set HTTP method to GET.
        connection.setRequestMethod("GET");
        // Already true by default but setting just in case; needs to be true since this request
        // is carrying an input (response) body.
        connection.setDoInput(true);
        // Open communications link (network traffic occurs here).
        connection.connect();
        return connection;
    }

}