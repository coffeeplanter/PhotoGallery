package ru.coffeeplanter.photogallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment {

    private static final String TAG = "PhotoGalleryFragment";

    private final int COLUMN_WIDTH = 200;
    private final int PRELOADED_BITMAPS_COUNT = 20;

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private PhotoAdapter mAdapter = new PhotoAdapter(mItems);
    private int totalPages = 1;
    private int currentPage = 1;
    private int itemsPerPage = 100;
    private boolean isLoadingData = false;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        updateItems();
        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
            @Override
            public void onThumbnailDownloaded(PhotoHolder photoHolder, Bitmap bitmap) {
                if (!isAdded()) return;
                Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                photoHolder.bindDrawable(drawable);
            }
        });
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.photo_recycler_view);
        final GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 3);
        mPhotoRecyclerView.setLayoutManager(gridLayoutManager);
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                switch (newState) {
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        mThumbnailDownloader.clearPreloadQueue();
                        break;
                    case RecyclerView.SCROLL_STATE_IDLE:
                        int firstVisibleItemPosition = gridLayoutManager.findFirstVisibleItemPosition();
                        int lastVisibleItemPosition = gridLayoutManager.findLastVisibleItemPosition();
                        int previousNumberToPreload;
                        if (firstVisibleItemPosition > PRELOADED_BITMAPS_COUNT / 2) {
                            previousNumberToPreload = PRELOADED_BITMAPS_COUNT / 2;
                        } else {
                            previousNumberToPreload = firstVisibleItemPosition;
                        }
                        int forwardNumberToPreload;
                        if (mItems.size() - lastVisibleItemPosition + 1 > PRELOADED_BITMAPS_COUNT / 2) {
                            forwardNumberToPreload = PRELOADED_BITMAPS_COUNT / 2;
                        } else {
                            forwardNumberToPreload = mItems.size() - lastVisibleItemPosition;
                        }
                        try {
                            mThumbnailDownloader.loadCache(
                                    mItems.subList(
                                            firstVisibleItemPosition - previousNumberToPreload,
                                            lastVisibleItemPosition + forwardNumberToPreload
                                    )
                            );
                        } catch (IndexOutOfBoundsException iobe) {
                            Log.e(TAG, String.format("Error getting sublist of GalleryItem objects: %s",
                                    iobe.getLocalizedMessage()), iobe);
                            return;
                        }
                        break;
                }
            }
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int visibleItemsCount = gridLayoutManager.getChildCount();
                int notVisiblePastItemsCount = gridLayoutManager.findFirstVisibleItemPosition();
                int totalItemsCount = gridLayoutManager.getItemCount();
                if ((visibleItemsCount + notVisiblePastItemsCount) >= totalItemsCount) {
                    if ((currentPage < totalPages) && (!isLoadingData)) {
                        Toast.makeText(getActivity(), R.string.loading_next_page_message, Toast.LENGTH_SHORT).show();
                        String query = QueryPreferences.getStoredQuery(getActivity());
                        new FetchItemsTask(query).execute(currentPage + 1);
                    }
                }
            }
        });

        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mPhotoRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int columnsCount = mPhotoRecyclerView.getWidth() / COLUMN_WIDTH;
                gridLayoutManager.setSpanCount(columnsCount);
            }
        });

        setupAdapter(true);
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearPreloadQueue();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();

        String query = QueryPreferences.getStoredQuery(getActivity());
        if (query != null) {
            searchView.setQuery(query, false);
            searchView.setIconified(false);
            searchView.clearFocus();
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "QueryTextSubmit: " + query);
                QueryPreferences.setStoredQuery(getActivity(), query);
                mItems.clear();
                updateItems();
                switchKeyboard(false);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "QueryTextChange: " + newText);
                return false;
            }
        });

        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                QueryPreferences.setStoredQuery(getActivity(), null);
                mItems.clear();
                updateItems();
                return false;
            }
        });

//        searchView.setOnSearchClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                String query = QueryPreferences.getStoredQuery(getActivity());
//                searchView.setQuery(query, false);
//            }
//        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                mItems.clear();
                updateItems();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems() {
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query).execute(currentPage);
    }

    private void setupAdapter(boolean newList) {
        if (isAdded()) {
            if (mPhotoRecyclerView.getAdapter() != null) {
                if (newList) {
                    mAdapter.notifyDataSetChanged();
                } else {
                    mAdapter.notifyItemRangeInserted(currentPage * itemsPerPage, itemsPerPage);
                }
            } else {
                mPhotoRecyclerView.setAdapter(mAdapter);
            }
        }
    }

    // Show / hide keyboard.
    private void switchKeyboard(boolean show) {
        View view = getActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (show) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                Log.d(TAG, "Keyboard has to be forced open");
            } else {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                Log.d(TAG, "Keyboard has to be forced close");
            }
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;
        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
        }
        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mGalleryItems;
        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }
        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.list_item_gallery, parent, false);
            return new PhotoHolder(view);
        }
        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            Drawable placeHolder = ContextCompat.getDrawable(getActivity(), R.drawable.image_placeholder);
            holder.bindDrawable(placeHolder);
            mThumbnailDownloader.queueThumbnail(holder, galleryItem.getUrl());
        }
        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<Integer, Void, List<GalleryItem>> {

        private String mQuery;
        int[] mPhotoListParameters;

        public FetchItemsTask(String query) {
            mQuery = query;
        }

        @Override
        protected void onPreExecute() {
            isLoadingData = true;
        }

        @Override
        protected List<GalleryItem> doInBackground(Integer... params) {
            FlickrFetchr flickFetchr = new FlickrFetchr();
            List<GalleryItem> galleryItemsList;
            if (mQuery == null) {
                galleryItemsList = flickFetchr.fetchRecentPhotos(params[0]);
            } else {
                galleryItemsList = flickFetchr.searchPhotos(mQuery, params[0]);
            }
//            List<GalleryItem> galleryItemsList = flickFetchr.downloadGalleryItems(params[0]);
            mPhotoListParameters =  flickFetchr.parsePhotoListParameters();
            return galleryItemsList;
        }
        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            boolean newList = false;
            if (mItems.isEmpty()) {
                newList = true;
            }
            mItems.addAll(items);
            setupAdapter(newList);
            if (mPhotoListParameters != null) {
                totalPages = mPhotoListParameters[0];
                currentPage = mPhotoListParameters[1];
                itemsPerPage = mPhotoListParameters[2];
            }
            isLoadingData = false;
        }
    }

}
