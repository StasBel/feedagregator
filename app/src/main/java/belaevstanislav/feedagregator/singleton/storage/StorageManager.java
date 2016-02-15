package belaevstanislav.feedagregator.singleton.storage;

import android.app.Activity;

import belaevstanislav.feedagregator.FeedAgregator;
import belaevstanislav.feedagregator.singleton.SignletonManager;

public class StorageManager implements SignletonManager {
    private static Storage storage;

    public static void initialize() {
        storage = new Storage(FeedAgregator.getContext());
    }

    public static Storage getInstance() {
        return storage;
    }
}
