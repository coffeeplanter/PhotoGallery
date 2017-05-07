package ru.coffeeplanter.photogallery;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment {

    private static final String TAG = "PhotoGalleryFragment";

    private final int COLUMN_WIDTH = 200;

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private PhotoAdapter mAdapter = new PhotoAdapter(mItems);
    private int totalPages = 1;
    private int currentPage = 1;
    private int itemsPerPage = 100;
    private boolean isLoadingData = false;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        new FetchItemsTask().execute(currentPage);
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
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int visibleItemsCount = gridLayoutManager.getChildCount();
                int notVisiblePastItemsCount = gridLayoutManager.findFirstVisibleItemPosition();
                int totalItemsCount = gridLayoutManager.getItemCount();
                if ((visibleItemsCount + notVisiblePastItemsCount) >= totalItemsCount) {
                    if ((currentPage < totalPages) && (!isLoadingData)) {
                        Toast.makeText(getActivity(), R.string.loading_next_page_message, Toast.LENGTH_SHORT).show();
                        new FetchItemsTask().execute(currentPage + 1);
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

        setupAdapter();
        return v;
    }

    private void setupAdapter() {
        if (isAdded()) {
            if (mPhotoRecyclerView.getAdapter() != null) {
                mAdapter.notifyItemRangeInserted(currentPage * itemsPerPage, itemsPerPage);
            } else {
                mPhotoRecyclerView.setAdapter(mAdapter);
            }
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private TextView mTitleTextView;
        public PhotoHolder(View itemView) {
            super(itemView);
            mTitleTextView = (TextView) itemView;
        }
        public void bindGalleryItem(GalleryItem item) {
            mTitleTextView.setText(item.toString());
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mGalleryItems;
        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }
        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView textView = new TextView(getActivity());
            return new PhotoHolder(textView);
        }
        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            holder.bindGalleryItem(galleryItem);
        }
        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<Integer, Void, List<GalleryItem>> {
        int[] photoListParameters;

        @Override
        protected void onPreExecute() {
            isLoadingData = true;
        }

        @Override
        protected List<GalleryItem> doInBackground(Integer... params) {
//            try {
//                String result = new FlickrFetchr().getUrlString("https://www.bignerdranch.com");
//                Log.i(TAG, "Fetched contents of URL: " + result);
//            } catch (IOException ioe) {
//                Log.e(TAG, "Failed to fetch URL: ", ioe);
//            }
            FlickrFetchr flickFetchr = new FlickrFetchr();
            List<GalleryItem> galleryItemsList = flickFetchr.fetchItems(params[0]);
            photoListParameters =  flickFetchr.parsePhotoListParameters();
            return galleryItemsList;
        }
        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            mItems.addAll(items);
            setupAdapter();
            if (photoListParameters != null) {
                totalPages = photoListParameters[0];
                currentPage = photoListParameters[1];
                itemsPerPage = photoListParameters[2];
            }
            isLoadingData = false;
        }
    }

}
