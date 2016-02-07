package org.zumh.android.photogallery;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;


public class PhotoGalleryFragment extends VisibleFragment {
    private static final String TAG = "PhotoGalleryFragment";
    private static final int GRID_VIEW_NUM_SPANS = 3;
    private static final int NEW_QUERY = 1;
    private static final int APPEND_QUERY = 0;

    private RecyclerView mPhotoRecyclerView;
    private LinearLayout mProgressBarLayout;
    private SearchView mSearchView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private int mRecentPhotosLastFetchedPage = 0;
    private int mSearchPhotosLastFetchedPage = 0;
    private boolean mLoadingData = false;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        updateItems(NEW_QUERY, true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

        mProgressBarLayout = (LinearLayout) v.findViewById(R.id.progressbar_ll_container);

        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.fragment_photo_gallery_recycler_view);
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), GRID_VIEW_NUM_SPANS));
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                PhotoAdapter adapter = (PhotoAdapter) recyclerView.getAdapter();
                int lastPosition = adapter.getLastBoundPosition();

                GridLayoutManager gridLayoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                int loadBufferPosition = (int) Math.ceil(GRID_VIEW_NUM_SPANS * 2.5);

                if (lastPosition >= (adapter.getItemCount() - gridLayoutManager.getSpanCount() - loadBufferPosition) && !mLoadingData) {
                    updateItems(APPEND_QUERY);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });

        setupAdapter();

        return v;
    }

    private void setupAdapter() {
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private int getNextRecentPhotosPageToFetch() {
        return mRecentPhotosLastFetchedPage + 1;
    }

    private int getNextSearchPhotosPageToFetch() {
        return mSearchPhotosLastFetchedPage + 1;
    }

    private void resetLastPagesFetched() {
        mRecentPhotosLastFetchedPage = 0;
        mSearchPhotosLastFetchedPage = 0;
    }

    private void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();

        if (view != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void collapseSearchView() {
        mSearchView.onActionViewCollapsed();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        mSearchView = (SearchView) searchItem.getActionView();

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "QueryTextSubmit: " + query);
                QueryPreferences.setStoredQuery(getActivity(), query);
                updateItems(NEW_QUERY);

                hideSoftKeyboard(getActivity());
                collapseSearchView();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "QueryTextChange: " + newText);
                return false;
            }
        });

        mSearchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = QueryPreferences.getStoredQuery(getActivity());
                mSearchView.setQuery(query, false);
            }
        });

        MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);
        if (PollService.isServiceAlarmOn(getActivity())) {
            toggleItem.setTitle(R.string.stop_polling);
        } else {
            toggleItem.setTitle(R.string.start_polling);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                collapseSearchView();
                hideSoftKeyboard(getActivity());
                updateItems(NEW_QUERY);
                return true;
            case R.id.menu_item_toggle_polling:
                boolean shouldStartAlarm = !PollService.isServiceAlarmOn(getActivity());
                PollService.setServiceAlarm(getActivity(), shouldStartAlarm);
                getActivity().invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems(int query_type, boolean skipSetVisibility) {
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query, skipSetVisibility, query_type).execute();
    }

    private void updateItems(int query_type) {
        updateItems(query_type, false);
    }

    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {
        private String mQuery;
        private boolean mNewData;  // whether or not the current data should be cleared
        private boolean mSkipPreExeVisibilitySet = true;

        public FetchItemsTask(String query, boolean skipPreExeVisibilitySet, int query_type) {
            mQuery = query;
            mSkipPreExeVisibilitySet = skipPreExeVisibilitySet;
            mNewData = query_type == NEW_QUERY;
        }

        @Override
        protected void onPreExecute() {
            mLoadingData = true;

            if (!mSkipPreExeVisibilitySet && mNewData) {
                mProgressBarLayout.setVisibility(View.VISIBLE);
                mPhotoRecyclerView.setVisibility(View.GONE);
            }
        }

        @Override
        protected List<GalleryItem> doInBackground(Void... params) {
            if (mNewData) {
                resetLastPagesFetched();
            }

            if (mQuery == null) {
                Log.i(TAG, "Getting page: " + getNextRecentPhotosPageToFetch());
                return new FlickrFetchr().fetchRecentPhotos(getNextRecentPhotosPageToFetch());
            } else {
                Log.i(TAG, "Getting page: " + getNextSearchPhotosPageToFetch());
                return new FlickrFetchr().searchPhotos(mQuery, getNextSearchPhotosPageToFetch());
            }
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            int lastFetchedPage = mQuery == null ? mRecentPhotosLastFetchedPage : mSearchPhotosLastFetchedPage;

            if (lastFetchedPage >= 1 && !mNewData) {
                mItems.addAll(items);
                mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
            } else if (mNewData) {
                mItems = items;
                setupAdapter();
            }

            if (mQuery == null) {
                ++mRecentPhotosLastFetchedPage;
            } else {
                ++mSearchPhotosLastFetchedPage;
            }

            mLoadingData = false;

            if (mNewData) {
                mProgressBarLayout.setVisibility(View.GONE);
                mPhotoRecyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = (ImageView) itemView.findViewById(R.id.fragment_photo_gallery_image_view);
        }

        public void bindGalleryItem(GalleryItem galleryItem) {
            Picasso.with(getActivity())
                    .load(galleryItem.getUrl())
                    .placeholder(R.drawable.placeholder)
                    .into(mItemImageView);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mGalleryItems;
        private int mLastBoundPosition;

        public int getLastBoundPosition() {
            return mLastBoundPosition;
        }

        public void setLastBoundPosition(int lastBoundPosition) {
            mLastBoundPosition = lastBoundPosition;
        }

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, parent, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            holder.bindGalleryItem(galleryItem);
            setLastBoundPosition(position);
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }
}
