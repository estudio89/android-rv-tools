package br.com.estudio89.rv_tools;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;

/**
 * Created by luccascorrea on 1/21/16.
 *
 */
public abstract class DataLoadingRecyclerViewAdapter<VH extends RecyclerView.ViewHolder> extends CursorRecyclerViewAdapter<VH> implements LoaderManager.LoaderCallbacks<Cursor> {

    // Two view types which will be used to determine whether a row should be displaying
    // data or a Progressbar
    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_LOADING = 1;
    public static final int VIEW_TYPE_ACTIVITY = 2;

    private Class viewHolderClass;
    private DataLoader dataLoadListener;
    private ItemClickListener clickListener;

    @Nullable private WeakReference<View> wkEmptyView;
    @Nullable private WeakReference<View> wkRecyclerView;
    @Nullable private VH headerViewHolder;

    @NonNull private Context context;
    private LoaderManager loaderManager;

    public interface DataLoader {
        void onLoadMore();
        boolean displayProgress();
    }

    public interface ItemClickListener {
        boolean onItemClick(View view, int position, RecyclerView.ViewHolder viewHolder);
    }

    public interface FullItemClickListener extends ItemClickListener {
        boolean onItemLongClick(View view, int position, RecyclerView.ViewHolder viewHolder);
    }

    /**
     * For initializing variables, don't do it in this method, see {@link #initialize(Object, LoaderManager, Object...)}.
     * @param activity the activity where the recycler view is placed
     * @param loaderManager getSupportLoaderManager();
     * @param args initialization arguments
     */

    public DataLoadingRecyclerViewAdapter(@NonNull Activity activity, @NonNull LoaderManager loaderManager, Object... args) {
        super(null);
        this.context = activity;
        initialize(activity, loaderManager, args);
    }

    /**
     * For initializing variables, don't do it in this method, see {@link #initialize(Object, LoaderManager, Object...)}.
     * @param fragment the fragment where the recycler view is placed
     * @param loaderManager fragment.getLoaderManager();
     * @param args initialization arguments
     */
    public DataLoadingRecyclerViewAdapter(Fragment fragment, @NonNull LoaderManager loaderManager, Object... args) {
        super(null);
        this.context = fragment.getActivity();
        initialize(fragment, loaderManager, args);
    }

    /**
     * When initializing variables, never do it in the constructor, always override this method,
     * calling super at the last line.
     *
     * @param fragmentOrActivity the fragment of activity passed to the constructor
     * @param loaderManager the loadermanager passed to the constructor
     * @param args array of extra arguments passed to the constructor
     */
    protected void initialize(Object fragmentOrActivity, @NonNull LoaderManager loaderManager, Object... args) {
        try {
            this.dataLoadListener = (DataLoader) fragmentOrActivity;
        } catch (ClassCastException e) {
            throw new RuntimeException(((Object) fragmentOrActivity).getClass().getSimpleName() + " must implement DataLoader.");
        }
        this.viewHolderClass = ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);


        this.loaderManager = loaderManager;
        this.headerViewHolder = this.createHeaderViewHolder();
        this.loaderManager.initLoader(0, null, this);

    }

    public void setItemClickListener(ItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setEmptyView(@NonNull RecyclerView recyclerView, @NonNull View emptyView) {
        wkEmptyView = new WeakReference<>(emptyView);
        wkRecyclerView = new WeakReference<View>(recyclerView);

        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {

            @Override
            public void onChanged() {
                super.onChanged();
                if (wkEmptyView != null && wkRecyclerView != null) {
                    View emptyView = wkEmptyView.get();
                    View recyclerView = wkRecyclerView.get();
                    if (emptyView != null && recyclerView != null) {
                        if (getItemCount() == 1 && !shouldDisplayProgress()) {
                            emptyView.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            emptyView.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                        }

                    }
                }

            }
        });
    }

    public void setHeaderView(View view) {
        try {
            Constructor constructor = this.viewHolderClass.getConstructor(this.getClass(), View.class);
            this.headerViewHolder = (VH) constructor.newInstance(this, view);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean shouldDisplayProgress() {
        return !isDataValid() || this.dataLoadListener.displayProgress();
    }


    @Override
    public int getItemCount() {
        // This is for adding the progress bar
        int count = super.getItemCount() + 1;

        if (this.headerViewHolder != null) {
            count += 0;
        }

        return count;
    }

    /**
     * This method offsets the position in case there is a header view.
     *
     * @param originalPosition the original position.
     * @return new position after offset.
     */
    @Override
    protected int getPosition(int originalPosition) {
        if (headerViewHolder != null) {
            originalPosition -= 1;
        }

        return originalPosition;
    }

    @Override
    public void onBindViewHolder(final VH viewHolder, final int position) {
        if (position != 0 || headerViewHolder == null) {
            int superCount = super.getItemCount();
            if (getPosition(position) < superCount && isDataValid()) {
                super.onBindViewHolder(viewHolder, position);
                viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (clickListener != null) {
                            clickListener.onItemClick(v, position, viewHolder);
                        }
                    }
                });

                viewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (clickListener != null && clickListener instanceof FullItemClickListener) {
                            FullItemClickListener fullListener = (FullItemClickListener) clickListener;
                            fullListener.onItemLongClick(viewHolder.itemView, position, viewHolder);
                        }
                        return false;
                    }
                });
            } else {
                if (shouldDisplayProgress()) {
                    viewHolder.itemView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                } else {
                    viewHolder.itemView.setLayoutParams(new ViewGroup.LayoutParams(0,0));
                }

            }
        }

    }

    protected VH createHeaderViewHolder() {
        return null;
    }

    protected abstract VH createLoadingViewHolder(ViewGroup parent);

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_HEADER:
                return this.headerViewHolder;
            case VIEW_TYPE_LOADING:
                return createLoadingViewHolder(parent);
            default:
                return onCreateDataViewHolder(parent, viewType);
        }
    }

    public abstract VH onCreateDataViewHolder(ViewGroup parent, int viewType);

    @Override
    public int getItemViewType(int position) {
        if (position == 0 && headerViewHolder != null) {
            return VIEW_TYPE_HEADER;
        } else {
            position = getPosition(position);
            return (position >= super.getItemCount()) ? VIEW_TYPE_LOADING : VIEW_TYPE_ACTIVITY;
        }

    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorDataLoader(this.context, this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        changeCursor(data);
        if (data.getCount() == 0 && this.dataLoadListener.displayProgress()) {
            this.dataLoadListener.onLoadMore();
        }
    }

    @NonNull
    public Context getContext() {
        return context;
    }

    public void refresh() {
        if (!this.loaderManager.hasRunningLoaders()) {
            this.loaderManager.restartLoader(0, null, this);
        }
    }

    public abstract Cursor getLoaderCursor();

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        changeCursor(null);
    }

    public static class CursorDataLoader extends SimpleCursorLoader {
        private WeakReference<DataLoadingRecyclerViewAdapter> wkAdapter;
        public CursorDataLoader(Context context, DataLoadingRecyclerViewAdapter adapter) {
            super(context);
            this.wkAdapter = new WeakReference<>(adapter);
        }

        @Override
        public Cursor loadInBackground() {
            if (this.wkAdapter.get() != null) {
                DataLoadingRecyclerViewAdapter adapter = this.wkAdapter.get();
                return adapter.getLoaderCursor();
            }
            return null;
        }
    }
}
