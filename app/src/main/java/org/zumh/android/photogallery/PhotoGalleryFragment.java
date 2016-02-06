package org.zumh.android.photogallery;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;


public class PhotoGalleryFragment extends Fragment {
    private static final String TAG = "PhotoGalleryFragment";
    private static final int GRID_VIEW_NUM_SPANS = 3;

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private int mLastFetchedPage = 0;
    private boolean mLoadingData = false;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        new FetchItemsTask().execute();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

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
                    new FetchItemsTask().execute();
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

    private int getNextPageToFetch() {
        return mLastFetchedPage + 1;
    }

    private class FetchItemsTask extends AsyncTask<Integer, Void, List<GalleryItem>> {
        @Override
        protected List<GalleryItem> doInBackground(Integer... params) {
            mLoadingData = true;
            Log.i(TAG, "Getting page: " + getNextPageToFetch());
            return new FlickrFetchr().fetchItems(getNextPageToFetch());
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            if (mLastFetchedPage >= 1) {
                mItems.addAll(items);
                mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
            } else {
                mItems = items;
                setupAdapter();
            }

            ++mLastFetchedPage;
            mLoadingData = false;
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
