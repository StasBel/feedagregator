package belaevstanislav.feedagregator.data.threadpool.task.getter;

import android.util.Log;

import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.models.Tweet;

import java.text.ParseException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import belaevstanislav.feedagregator.data.Data;
import belaevstanislav.feedagregator.data.storage.StorageKey;
import belaevstanislav.feedagregator.service.util.Latch;
import belaevstanislav.feedagregator.feeditem.core.TWITTERFeedItemCore;
import belaevstanislav.feedagregator.data.threadpool.task.parser.TWITTERParserTask;
import belaevstanislav.feedagregator.util.Constant;

public class TWITTERGetterTask extends GetterTask implements Runnable {
    private final Latch latch;

    public TWITTERGetterTask(Data data, Latch latch) {
        super(data);
        this.latch = latch;
    }

    private class HandleItemTask extends GetterTask implements Runnable {
        private final Tweet tweet;
        private final CountDownLatch countDownLatch;

        public HandleItemTask(Data data, Tweet tweet, CountDownLatch countDownLatch) {
            super(data);
            this.tweet = tweet;
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            try {
                TWITTERFeedItemCore core = new TWITTERFeedItemCore(tweet);
                long id = getData().database.insertCore(core);
                countDownLatch.countDown();

                getData().taskPool.submitParserTask(new TWITTERParserTask(getData(), core, id, tweet));
            } catch (ParseException parseException) {
                Log.e("TWITTER", "TWITTER_PARSECORE_EXCEPTION");
                parseException.printStackTrace();
            }
        }
    }

    private class HandleItemsTask extends GetterTask implements Runnable {
        private final Result<List<Tweet>> result;

        public HandleItemsTask(Data data, Result<List<Tweet>> result) {
            super(data);
            this.result = result;
        }

        @Override
        public void run() {
            List<Tweet> tweetList = result.data;
            int size = tweetList.size();
            if (size > 0) {
                getData().storage.saveLong(StorageKey.LAST_TWEET_ID, tweetList.get(0).getId());

                CountDownLatch countDownLatch = new CountDownLatch(size);
                Data data = getData();
                for (int index = 0; index < size; index++) {
                    data.taskPool.submitRunnableTask(new HandleItemTask(data, tweetList.get(index), countDownLatch));
                }

                try {
                    countDownLatch.await();
                } catch (InterruptedException exception) {
                    Log.e("TWITTER", "TWITTER_THREADS_EXCEPTION");
                    exception.printStackTrace();
                }
                latch.countDownAndTryNotify();
            }
        }
    }

    @Override
    public void run() {
        // TODO реализовать проход по страницам (результатов может быть больше, чем 200) + id
        Integer number;
        //Long id;
        if (!getData().storage.isInMemory(StorageKey.LAST_TWEET_ID)) {
            number = Constant.FIRST_TWITTER_QUERY_PAGE_SIZE;
            //id = null;
        } else {
            number = Constant.MAX_TWEETS_PER_PAGE;
            // TODO delete 10000000
            //id = StorageManager.getInstance().getLong(StorageKey.LAST_TWEET_ID) - 100000000;
        }

        TwitterCore
                .getInstance()
                .getApiClient()
                .getStatusesService()
                .homeTimeline(number, null, null, null, null, null, null, new TWITTERCallback());
    }

    private class TWITTERCallback extends Callback<List<Tweet>> {
        @Override
        public void success(Result<List<Tweet>> result) {
            getData().taskPool.submitRunnableTask(new HandleItemsTask(getData(), result));
        }

        @Override
        public void failure(TwitterException twitterException) {
            Log.e("TWITTER", "TWITTER_GET_EXCEPTION");
            twitterException.printStackTrace();
        }
    }
}

