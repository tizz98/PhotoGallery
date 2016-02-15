package org.zumh.android.photogallery;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

public class PhotoPageFragment extends VisibleFragment {
    private static final String ARG_URI = "photo_page_url";

    private Uri mUri;
    private WebView mWebView;
    private ProgressBar mProgressBar;
    private Menu mMenu;

    public static PhotoPageFragment newInstance(Uri uri) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, uri);

        PhotoPageFragment fragment = new PhotoPageFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUri = getArguments().getParcelable(ARG_URI);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        final View v = inflater.inflate(R.layout.fragment_photo_page, container, false);

        mProgressBar = (ProgressBar) v.findViewById(R.id.fragment_photo_page_progess_bar);
        mProgressBar.setMax(100);  // WebChromeClient reports in range 0-100

        mWebView = (WebView) v.findViewById(R.id.fragment_photo_page_web_view);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    mProgressBar.setVisibility(View.GONE);
                } else {
                    mProgressBar.setVisibility(View.VISIBLE);
                    mProgressBar.setProgress(newProgress);
                }
            }
        });
        mWebView.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
                } else {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    PackageManager pm = getActivity().getPackageManager();

                    if (pm.resolveActivity(i, 0) != null) {
                        startActivity(i);
                    }

                    return true;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (mMenu == null) {
                    return;
                }

                toggleGoBackwardState(view.canGoBack());
                toggleGoForwardState(view.canGoForward());
            }
        });
        mWebView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    WebView webView = (WebView) v;

                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                            if (webView.canGoBack()) {
                                webView.goBack();
                                return true;
                            }
                            break;
                    }
                }

                return false;
            }
        });
        mWebView.loadUrl(mUri.toString());

        return v;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_page, menu);

        mMenu = menu;
        toggleGoForwardState(false);
        toggleGoBackwardState(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_go_back:
                if (mWebView.canGoBack()) {
                    mWebView.goBack();
                }
                return true;
            case R.id.menu_item_go_forward:
                if (mWebView.canGoForward()) {
                    mWebView.goForward();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void toggleGoForwardState(boolean enabled) {
        toggleArrowMenuBtnState(enabled, R.id.menu_item_go_forward, R.drawable.ic_go_forward_enabled, R.drawable.ic_go_forward_disabled);
    }

    private void toggleGoBackwardState(boolean enabled) {
        toggleArrowMenuBtnState(enabled, R.id.menu_item_go_back, R.drawable.ic_go_back_enabled, R.drawable.ic_go_back_disabled);
    }

    private void toggleArrowMenuBtnState(boolean enabled, int menuId, int enabledDrawableId, int disabledDrawableId) {
        int iconId = enabled ? enabledDrawableId : disabledDrawableId;
        MenuItem menuItem = mMenu.findItem(menuId);

        menuItem.setEnabled(enabled);
        menuItem.setIcon(iconId);
    }
}
