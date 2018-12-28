package haselmehri.app.com.elivideoplayer;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.media.SubtitleData;
import android.media.TimedText;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.jaiselrahman.filepicker.activity.FilePickerActivity;
import com.jaiselrahman.filepicker.config.Configurations;
import com.jaiselrahman.filepicker.model.MediaFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import haselmehri.app.com.elivideoplayer.SQLiteHelper.VideoPlayerSQLiteHelper;
import haselmehri.app.com.elivideoplayer.model.Favorite;

public class VideoPlayerActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "VideoPlayerActivity";
    private static final int REQUEST_CODE_PICK_VIDEO = 1002;
    private static final int REQUEST_CODE_PICK_FILE_FOLDER = 1003;
    private static final int REQUEST_CODE_PICK_SUBTITLE = 1004;
    public static final int PERMISSIONS_REQUEST_CODE_READ_EXTERNAL_STORAGE_TO_SUBTITLE = 130;
    private TextView txtCurrentDuration;
    private TextView txtVideoDuration;
    private ImageView playButton;
    private SeekBar seekBar;
    private Timer timer;
    private TextView txtVideoInfo;
    private ArrayList<MediaFile> mediaFiles = new ArrayList<>();
    private ArrayList<String> mediaFilesSubtitleList = new ArrayList<>();
    private TextView txtSelectedCountVideo;
    private int currentVideoIndex = 0;
    private boolean videoPlayer_isPlaying = false;
    private View relativeLayoutButtons;
    RelativeLayout.LayoutParams matchParentLayoutParams;
    private boolean visisbleRelativeLayoutButtonsInLandscape = true;
    private FrameLayout frameLayout;
    private CoordinatorLayout videoPlayerCoordinator;
    private Snackbar snackbar;
    private DrawerLayout drawerLayout;
    private VideoPlayerSQLiteHelper videoPlayerSQLiteHelper;
    private MediaPlayer mediaPlayer;
    private TextView txtSubtitle;
    private SurfaceHolder surfaceHolder;
    private SurfaceView playerSurfaceView;
    private ImageView favoriteImage;
    private ImageView resizeVideoImage;
    private Boolean isFavorite = false;
    private Boolean hasActiveHolder = false;
    private String filePath;
    private Boolean isStopActivity = false;
    private int videoWidth;
    private int videoHeight;
    private int mainVideoWidth;
    private int mainVideoHeight;
    private boolean isFullScreen = true;
    private boolean isDoingChangeSubtitle = false;
    private int mediaPlayer_currentPosition = 0;
    private boolean firstAccessPermissionToStorage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate: ");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        setupViews();
        doubleTouchHandler();
        setupToolbar();
        setupNavigationView();

        frameLayout.bringToFront();
        frameLayout.setLayoutParams(matchParentLayoutParams);
        relativeLayoutButtons.bringToFront();
        seekBar.bringToFront();

        Intent intent = getIntent();
        if (intent.getAction() != null && intent.getAction().equals("android.intent.action.VIEW")) {
            boolean result = Utilities.checkPermission(VideoPlayerActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE,
                    Utilities.PERMISSIONS_REQUEST_CODE_READ_EXTERNAL_STORAGE_COMPLETE_ACTION_USING, "Access to Read External Storage is required!", "Access Dialog", "Yes", "No");
            if (result)
                playVideoFromCompleteActionUsingWindow();
        }
    }

    private void playVideoFromCompleteActionUsingWindow() {
        String realPath = Utilities.getUriRealPath(this, getIntent().getData()).replace("/mnt/media_rw", "/storage");
        File file = new File(realPath);
        if (file.exists()) {
            MediaFile mediaFile = new MediaFile();
            mediaFile.setPath(realPath);
            mediaFiles.add(mediaFile);

            currentVideoIndex = 0;

            filePath = mediaFile.getPath();

            mediaFilesSubtitleList = new ArrayList<>();
            mediaFilesSubtitleList.add(loadSubtitleBaseVideoFilename(filePath));

            videoPlayer_isPlaying = true;
            playButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_pause, null));

            setFavoriteImage(filePath);
            if (firstAccessPermissionToStorage) {
                firstAccessPermissionToStorage = false;
                surfaceCreated(playerSurfaceView.getHolder());
            }
            seekBar.setEnabled(true);
        }
    }

    private void setFavoriteImage(String filePath) {
        if (videoPlayerSQLiteHelper.checkFavoriteExists(filePath)) {
            favoriteImage.setImageResource(R.drawable.ic_favorite_gold);
            isFavorite = true;
        } else {
            favoriteImage.setImageResource(R.drawable.ic_favorite_gray);
            isFavorite = false;
        }
    }

    private void setFavoriteStatus() {
        if (mediaFiles != null && mediaFiles.size() > 0) {
            MediaFile mediaFile = mediaFiles.get(currentVideoIndex);
            if (isFavorite) {
                if (videoPlayerSQLiteHelper.checkFavoriteExists(mediaFile.getPath())) {
                    if (videoPlayerSQLiteHelper.deleteFavorite(mediaFile.getPath()))
                        setFavoriteImage(mediaFile.getPath());
                } else {
                    setFavoriteImage(mediaFile.getPath());
                }
            } else {
                if (!videoPlayerSQLiteHelper.checkFavoriteExists(mediaFile.getPath())) {
                    Favorite favorite = new Favorite();
                    favorite.setFilePath(mediaFile.getPath());
                    if (videoPlayerSQLiteHelper.addFavorite(favorite))
                        setFavoriteImage(mediaFile.getPath());
                } else {
                    setFavoriteImage(mediaFile.getPath());
                }
            }
        }
    }

    private void loadFavoriteVideos() {
        List<Favorite> favorites = videoPlayerSQLiteHelper.getFavorites();
        if (favorites != null && favorites.size() > 0) {
            mediaFiles = new ArrayList<>();
            MediaFile mediaFile;
            for (Favorite favorite : favorites) {
                mediaFile = new MediaFile();
                mediaFile.setPath(favorite.getFilePath());
                mediaFiles.add(mediaFile);
            }
            txtSelectedCountVideo.setText(getResources().getString(R.string.label_video_1_of).concat(String.valueOf(mediaFiles.size())));
            currentVideoIndex = 0;
            txtVideoInfo.setText(mediaFiles.get(0).getPath().substring(mediaFiles.get(0).getPath().lastIndexOf("/") + 1));
            filePath = mediaFiles.get(0).getPath();
            videoPlayer_isPlaying = mediaPlayer.isPlaying();
            seekBar.setEnabled(true);

            surfaceCreated(playerSurfaceView.getHolder());
        } else {
            Snackbar snackbar = Snackbar.make(videoPlayerCoordinator, "No video  has been added to your Favorite list!", Snackbar.LENGTH_LONG);
            snackbar.getView().setBackgroundColor(ContextCompat.getColor(VideoPlayerActivity.this, R.color.color_orange));
            snackbar.show();
        }
    }

    private void selectFileOrFolder() {
        videoPlayer_isPlaying = mediaPlayer.isPlaying();
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType("video/*"); // Set MIME type as per requirement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            chooseFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        Intent intent = Intent.createChooser(chooseFile, "Choose videos");
        startActivityForResult(intent, REQUEST_CODE_PICK_FILE_FOLDER);
    }

    private void loadSubtitle() {
        videoPlayer_isPlaying = mediaPlayer.isPlaying();
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType("*/*");
        //chooseFile.setType("application/x-subrip"); // Set MIME type as per requirement
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        Intent intent = Intent.createChooser(chooseFile, "Choose a subtitle file");
        isDoingChangeSubtitle = true;
        startActivityForResult(intent, REQUEST_CODE_PICK_SUBTITLE);
    }

    private void setupToolbar() {
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        Toolbar toolbar = findViewById(R.id.video_player_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.color_orange_dark));

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        ActionBarDrawerToggle actionBarDrawerToggle = new
                ActionBarDrawerToggle(this, drawerLayout, toolbar, 0, 0);
        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();
    }

    private void setupNavigationView() {
        NavigationView navigationView = findViewById(R.id.navigation_view);
        navigationView.setItemIconTintList(null);

        Menu m = navigationView.getMenu();
        for (int i = 0; i < m.size(); i++) {
            MenuItem mi = m.getItem(i);

            //for aapplying a font to subMenu ...
            SubMenu subMenu = mi.getSubMenu();
            if (subMenu != null && subMenu.size() > 0) {
                for (int j = 0; j < subMenu.size(); j++) {
                    MenuItem subMenuItem = subMenu.getItem(j);
                    Utilities.applyFontToMenuItem(this, subMenuItem, BaseApplication.getIranianSansFont());
                }
            }

            //the method we have create in activity
            Utilities.applyFontToMenuItem(this, mi, BaseApplication.getIranianSansFont());
        }

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.navigation_menu_select_videos:
                        selectVideoFile();
                        break;
                    case R.id.navigation_menu_favorite_list:
                        loadFavoriteVideos();
                        break;
                    case R.id.navigation_menu_select_from_storage: {
                        boolean result = Utilities.checkPermission(VideoPlayerActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE,
                                Utilities.PERMISSIONS_REQUEST_CODE_READ_EXTERNAL_STORAGE, "Access to Read External Storage is required!", "Access Dialog", "Yes", "No");
                        if (result)
                            selectFileOrFolder();
                        break;
                    }
                    case R.id.navigation_menu_load_subtitle: {
                        boolean result = Utilities.checkPermission(VideoPlayerActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE,
                                PERMISSIONS_REQUEST_CODE_READ_EXTERNAL_STORAGE_TO_SUBTITLE, "Access to Read External Storage is required!", "Access Dialog", "Yes", "No");
                        if (result)
                            loadSubtitle();
                        break;
                    }
                  /*  case R.id.navigation_menu_setting:
                        //startActivityForResult(new Intent(VideoPlayerActivity.this, MusicPlayerSettingActivity.class), REQUSET_CODE_SETTING);
                        break;*/
                    case R.id.navigation_menu_exit:
                        finish();
                        break;
                }
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

        });
    }

    private void prepareViews(boolean isPlaying) {
        txtSubtitle.setText("");
        txtVideoDuration.setText(formatDuration(mediaPlayer.getDuration()));
        txtCurrentDuration.setText(formatDuration(0));
        seekBar.setMax(mediaPlayer.getDuration());

        if (mediaPlayer_currentPosition > 0) {
            mediaPlayer.seekTo(mediaPlayer_currentPosition);
            mediaPlayer_currentPosition = 0;
        }
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        timer = new Timer();
        timer.schedule(new MainTimer(), 0, 1000);

        if (isPlaying)
            mediaPlayer.start();
    }

    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        return displayMetrics;
    }

    private void setupViews() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        matchParentLayoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        videoPlayerCoordinator = findViewById(R.id.video_player_coordinator);
        mediaPlayer = new MediaPlayer();
        playerSurfaceView = findViewById(R.id.player_surface);
        relativeLayoutButtons = findViewById(R.id.buttons_relativeLayout);
        txtSubtitle = findViewById(R.id.subtitle_text);
        surfaceHolder = playerSurfaceView.getHolder();
        surfaceHolder.addCallback(this);
        //videoPlayer = findViewById(R.id.video_view);
        frameLayout = findViewById(R.id.frame_root);
        videoPlayerSQLiteHelper = new VideoPlayerSQLiteHelper(this);

        playButton = findViewById(R.id.play_button);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaFiles != null && mediaFiles.size() > 0) {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                        playButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_play, null));
                        defaultScreenDevice();
                    } else {
                        mediaPlayer.start();
                        playButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_pause, null));
                        fullScreenDevice();
                    }
                } else {
                    snackbar = Snackbar.make(videoPlayerCoordinator, getResources().getString(R.string.message_not_video_selected), Snackbar.LENGTH_LONG);
                    snackbar.getView().setBackgroundColor(ContextCompat.getColor(VideoPlayerActivity.this, R.color.color_orange));
                    snackbar.show();
                }
            }
        });

        txtVideoInfo = findViewById(R.id.video_info_textview);
        YoYo.with(Techniques.Shake).repeat(YoYo.INFINITE).interpolate(new BounceInterpolator()).duration(5000).playOn(txtVideoInfo);

        txtVideoDuration = findViewById(R.id.video_duration_text);
        txtVideoDuration.setText(formatDuration(0));

        txtCurrentDuration = findViewById(R.id.video_current_duration_text);
        txtCurrentDuration.setText(formatDuration(0));

        final ImageView forwardButton = findViewById(R.id.forward_button);
        forwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //videoPlayer.seekTo(videoPlayer.getCurrentPosition() + 10000);
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() + 10000);
            }
        });
        ImageView rewindButton = findViewById(R.id.rewind_button);
        rewindButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //videoPlayer.seekTo(videoPlayer.getCurrentPosition() - 10000);
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - 10000);
            }
        });

        ImageView skipNextButton = findViewById(R.id.skip_next_button);
        skipNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextVideoPlay();
            }
        });

        ImageView skipPreviousButton = findViewById(R.id.skip_previous_button);
        skipPreviousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PreviousVideoPlay();
            }
        });

        seekBar = findViewById(R.id.seek_bar);
        seekBar.setEnabled(false);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    //videoPlayer.seekTo(progress);
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        favoriteImage = findViewById(R.id.favorite_image);
        favoriteImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setFavoriteStatus();
            }
        });

        resizeVideoImage = findViewById(R.id.resize_image);
        resizeVideoImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isFullScreen && videoWidth != 0 && videoHeight != 0) {
                    isFullScreen = false;
                    resizeVideoImage.setImageResource(R.drawable.ic_stretch_black);
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) playerSurfaceView.getLayoutParams();
                    lp.width = videoWidth;
                    lp.height = videoHeight;
                    playerSurfaceView.setLayoutParams(lp);
                } else {
                    isFullScreen = true;
                    resizeVideoImage.setImageResource(R.drawable.ic_crop_black);
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) playerSurfaceView.getLayoutParams();
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    playerSurfaceView.setLayoutParams(lp);
                }
            }
        });

        txtSelectedCountVideo = findViewById(R.id.selected_video_count_textview);

        drawerLayout = findViewById(R.id.drawer_layout);
    }

    private void nextVideoPlay() {
        if (mediaFiles != null && mediaFiles.size() > 0) {
            if (currentVideoIndex < mediaFiles.size() - 1) {
                currentVideoIndex += 1;
                txtSelectedCountVideo.setText(getResources().getString(R.string.label_videos).concat(String.valueOf((currentVideoIndex + 1)))
                        .concat(getResources().getString(R.string.label_video_of)).concat(String.valueOf(mediaFiles.size())));
                MediaFile mediaFile = mediaFiles.get(currentVideoIndex);
                txtVideoInfo.setText(mediaFile.getPath().substring(mediaFile.getPath().lastIndexOf("/") + 1));
                filePath = mediaFile.getPath();
                videoPlayer_isPlaying = mediaPlayer.isPlaying();

                surfaceCreated(playerSurfaceView.getHolder());
            } else {
                txtSelectedCountVideo.setText(getResources().getString(R.string.label_video_1_of).concat(String.valueOf(mediaFiles.size())));
                currentVideoIndex = 0;
                MediaFile mediaFile = mediaFiles.get(0);
                txtVideoInfo.setText(mediaFile.getPath().substring(mediaFile.getPath().lastIndexOf("/") + 1));
                filePath = mediaFile.getPath();
                videoPlayer_isPlaying = mediaPlayer.isPlaying();

                surfaceCreated(playerSurfaceView.getHolder());
            }
        } else {
            snackbar = Snackbar.make(videoPlayerCoordinator, "No video selected for play!", Snackbar.LENGTH_LONG);
            snackbar.getView().setBackgroundColor(ContextCompat.getColor(VideoPlayerActivity.this, R.color.color_orange));
            snackbar.show();
        }
    }

    private void PreviousVideoPlay() {
        if (mediaFiles != null && mediaFiles.size() > 0) {
            if (currentVideoIndex > 0) {
                currentVideoIndex -= 1;
                txtSelectedCountVideo.setText(String.format("%s%s%s%s", getResources().getString(R.string.label_videos), (currentVideoIndex + 1), getResources().getString(R.string.label_video_of), mediaFiles.size()));
                MediaFile mediaFile = mediaFiles.get(currentVideoIndex);
                txtVideoInfo.setText(mediaFile.getPath().substring(mediaFile.getPath().lastIndexOf("/") + 1));
                filePath = mediaFile.getPath();
                videoPlayer_isPlaying = mediaPlayer.isPlaying();

                surfaceCreated(playerSurfaceView.getHolder());
            } else {
                txtSelectedCountVideo.setText(getResources().getString(R.string.label_videos).concat(String.valueOf(mediaFiles.size()))
                        .concat(getResources().getString(R.string.label_video_of)).concat(String.valueOf(mediaFiles.size())));
                currentVideoIndex = mediaFiles.size() - 1;
                MediaFile mediaFile = mediaFiles.get(currentVideoIndex);
                txtVideoInfo.setText(mediaFile.getPath().substring(mediaFile.getPath().lastIndexOf("/") + 1));
                filePath = mediaFile.getPath();
                videoPlayer_isPlaying = mediaPlayer.isPlaying();

                surfaceCreated(playerSurfaceView.getHolder());
            }
        } else {
            snackbar = Snackbar.make(videoPlayerCoordinator, "No video selected for play!", Snackbar.LENGTH_LONG);
            snackbar.getView().setBackgroundColor(ContextCompat.getColor(VideoPlayerActivity.this, R.color.color_orange));
            snackbar.show();
        }
    }

    private String formatDuration(long duration) {
        int seconds = (int) (duration / 1000);
        int minutes = seconds / 60;
        seconds %= 60;

        return String.format(Locale.ENGLISH, "%02d", minutes) + ":" + String.format(Locale.ENGLISH, "%02d", seconds);
    }

    private class MainTimer extends TimerTask {

        //بصورت پیش فرض در Therad اصلی اجرا نمیشود
        //پس از runOnUiThread استقاده میکنیم تا به Viewها دسترسی داشته باشیم
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    /*seekBar.setProgress(videoPlayer.getCurrentPosition());
                    seekBar.setSecondaryProgress((videoPlayer.getBufferPercentage() * videoPlayer.getDuration()) / 100);
                    txtCurrentDuration.setText(formatDuration(videoPlayer.getCurrentPosition()));*/
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());
                    //seekBar.setSecondaryProgress((mediaPlayer.getBufferPercentage() * mediaPlayer.getDuration()) / 100);
                    txtCurrentDuration.setText(formatDuration(mediaPlayer.getCurrentPosition()));
                }
            });
        }
    }

    private void selectVideoFile() {
        videoPlayer_isPlaying = mediaPlayer.isPlaying();
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.CONFIGS, new Configurations.Builder()
                .setCheckPermission(true)
                .setShowImages(false)
                .setShowVideos(true)
                .setShowAudios(false)
                .setMaxSelection(10)
                .build());
        startActivityForResult(intent, REQUEST_CODE_PICK_VIDEO);
    }

    private String loadSubtitleBaseVideoFilename(String videoPath) {
        String extension = videoPath.substring(videoPath.lastIndexOf("."));
        String subtitlePath = videoPath.replace(extension, ".srt");
        if (new File(subtitlePath).exists())
            return subtitlePath;

        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_PICK_VIDEO) {
                try {
                    //Do something with files
                    if (data.getParcelableArrayListExtra(FilePickerActivity.MEDIA_FILES).size() > 0) {
                        mediaFiles = data.getParcelableArrayListExtra(FilePickerActivity.MEDIA_FILES);
                        txtSelectedCountVideo.setText(getResources().getString(R.string.label_video_1_of).concat(String.valueOf(mediaFiles.size())));
                        currentVideoIndex = 0;
                        MediaFile mediaFile = mediaFiles.get(0);
                        txtVideoInfo.setText(mediaFile.getPath().substring(mediaFile.getPath().lastIndexOf("/") + 1));
                        filePath = mediaFile.getPath();
                        seekBar.setEnabled(true);

                        isStopActivity = false;

                        mediaFilesSubtitleList = new ArrayList<>();
                        for (MediaFile media : mediaFiles) {
                            mediaFilesSubtitleList.add(loadSubtitleBaseVideoFilename(media.getPath()));
                        }
                    } else {
                        Snackbar snackbar = Snackbar.make(videoPlayerCoordinator, getResources().getString(R.string.message_not_video_added_favorite), Snackbar.LENGTH_LONG);
                        snackbar.getView().setBackgroundColor(ContextCompat.getColor(VideoPlayerActivity.this, R.color.color_orange));
                        snackbar.show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Snackbar snackbar = Snackbar.make(videoPlayerCoordinator, getResources().getString(R.string.message_video_unknown_error), Snackbar.LENGTH_LONG);
                    snackbar.getView().setBackgroundColor(ContextCompat.getColor(VideoPlayerActivity.this, R.color.color_orange));
                    snackbar.show();
                }

            } else if (requestCode == REQUEST_CODE_PICK_SUBTITLE) {
                if (data != null && data.getData() != null) {
                    Uri uri = data.getData();
                    File file = new File(Utilities.getUriRealPath(this, uri).replace("/mnt/media_rw", "/storage"));
                    if (file.exists()) {
                        String extension = file.getPath().substring(file.getPath().lastIndexOf(".")).toLowerCase();
                        if (extension.equals(".srt")) {
                            if (mediaFilesSubtitleList.size() >= currentVideoIndex)
                                mediaFilesSubtitleList.set(currentVideoIndex, file.getPath());
                        }
                    }
                } else
                    isDoingChangeSubtitle = false;
            } else if (requestCode == REQUEST_CODE_PICK_FILE_FOLDER) {
                if (data != null && data.getData() != null) {
                    Uri uri = data.getData();
                    File file = new File(Utilities.getUriRealPath(this, uri).replace("/mnt/media_rw", "/storage"));
                    if (file.exists()) {
                        MediaFile mediaFile = new MediaFile();
                        mediaFile.setPath(file.getPath());
                        mediaFiles.clear();
                        mediaFiles.add(mediaFile);

                        txtSelectedCountVideo.setText(getResources().getString(R.string.label_video_1_of).concat(String.valueOf(mediaFiles.size())));
                        currentVideoIndex = 0;
                        txtVideoInfo.setText(mediaFiles.get(0).getPath().substring(mediaFiles.get(0).getPath().lastIndexOf("/") + 1));
                        filePath = mediaFiles.get(0).getPath();
                        seekBar.setEnabled(true);

                        isStopActivity = false;

                        mediaFilesSubtitleList = new ArrayList<>();
                        mediaFilesSubtitleList.add(loadSubtitleBaseVideoFilename(filePath));
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        ClipData clipData = data != null ? data.getClipData() : null;
                        if (clipData != null) {
                            ArrayList<MediaFile> tempMediaFiles = new ArrayList<>();
                            MediaFile mediaFile;
                            for (int i = 0; i < clipData.getItemCount(); i++) {
                                ClipData.Item item = clipData.getItemAt(i);
                                Uri uri = item.getUri();
                                String path = Utilities.getUriRealPath(this, uri).replace("/mnt/media_rw", "/storage");
                                if (new File(path).exists()) {
                                    mediaFile = new MediaFile();
                                    mediaFile.setPath(path);
                                    tempMediaFiles.add(mediaFile);
                                }
                            }
                            if (tempMediaFiles.size() > 0) {
                                mediaFiles = tempMediaFiles;

                                txtSelectedCountVideo.setText(getResources().getString(R.string.label_video_1_of).concat(String.valueOf(mediaFiles.size())));
                                currentVideoIndex = 0;
                                txtVideoInfo.setText(mediaFiles.get(0).getPath().substring(mediaFiles.get(0).getPath().lastIndexOf("/") + 1));
                                filePath = mediaFiles.get(0).getPath();
                                seekBar.setEnabled(true);

                                isStopActivity = false;

                                mediaFilesSubtitleList = new ArrayList<>();
                                for (MediaFile media : mediaFiles) {
                                    mediaFilesSubtitleList.add(loadSubtitleBaseVideoFilename(media.getPath()));
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Utilities.PERMISSIONS_REQUEST_CODE_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectFileOrFolder();
            } else {
                Snackbar snackbar = Snackbar.make(videoPlayerCoordinator, "You do not have access to Read External Storage!You can not select video from external storage!", Snackbar.LENGTH_LONG);
                snackbar.getView().setBackgroundColor(ContextCompat.getColor(VideoPlayerActivity.this, R.color.color_orange));
                snackbar.show();
            }
        } else if (requestCode == Utilities.PERMISSIONS_REQUEST_CODE_READ_EXTERNAL_STORAGE_COMPLETE_ACTION_USING) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                firstAccessPermissionToStorage = true;
                playVideoFromCompleteActionUsingWindow();
            } else {
                Snackbar snackbar = Snackbar.make(videoPlayerCoordinator, "You do not have access to Read External Storage!You can not select video from external storage!", Snackbar.LENGTH_LONG);
                snackbar.getView().setBackgroundColor(ContextCompat.getColor(VideoPlayerActivity.this, R.color.color_orange));
                snackbar.show();
            }
        } else if (requestCode == PERMISSIONS_REQUEST_CODE_READ_EXTERNAL_STORAGE_TO_SUBTITLE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSubtitle();
            } else {
                Snackbar snackbar = Snackbar.make(videoPlayerCoordinator, "You do not have access to Read External Storage!You can not select subtitle from external storage!", Snackbar.LENGTH_LONG);
                snackbar.getView().setBackgroundColor(ContextCompat.getColor(VideoPlayerActivity.this, R.color.color_orange));
                snackbar.show();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mainVideoWidth != 0 && mainVideoHeight != 0) {
            DisplayMetrics displayMetrics = getDisplayMetrics();
            if (mainVideoWidth <= displayMetrics.widthPixels)
                videoWidth = mainVideoWidth;
            else
                videoWidth = displayMetrics.widthPixels;

            if (mainVideoHeight <= displayMetrics.heightPixels)
                videoHeight = mainVideoHeight;
            else
                videoHeight = displayMetrics.heightPixels;


            if (!isFullScreen) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) playerSurfaceView.getLayoutParams();
                lp.width = videoWidth;
                lp.height = videoHeight;
                playerSurfaceView.setLayoutParams(lp);
            }
        }
        /*if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            frameLayout.setLayoutParams(portraitLayoutParams);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            frameLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.White));
            txtVideoInfo.bringToFront();
            txtSelectedCountVideo.bringToFront();
            getSupportActionBar().show();
        } else {
            frameLayout.setLayoutParams(landscapeLayoutParams);
            frameLayout.bringToFront();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            frameLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black));
            getSupportActionBar().hide();
        }*/
    }

    private void fullScreenDevice() {
        visisbleRelativeLayoutButtonsInLandscape = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().hide();
        relativeLayoutButtons.setVisibility(View.INVISIBLE);
        seekBar.setVisibility(View.INVISIBLE);
        frameLayout.bringToFront();
        frameLayout.setLayoutParams(matchParentLayoutParams);
    }

    private void defaultScreenDevice() {
        visisbleRelativeLayoutButtonsInLandscape = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().show();
        relativeLayoutButtons.setVisibility(View.VISIBLE);
        seekBar.setVisibility(View.VISIBLE);
        relativeLayoutButtons.bringToFront();
        seekBar.bringToFront();
    }

    private void doubleTouchHandler() {
        frameLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!visisbleRelativeLayoutButtonsInLandscape) {
                    defaultScreenDevice();
                } else {
                    fullScreenDevice();
                }
            }
        });
    }

    private int findTrackIndexFor(MediaPlayer.TrackInfo[] trackInfo) {
        int index = -1;
        for (int i = 0; i < trackInfo.length; i++) {
            if (trackInfo[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                return i;
            }
        }
        return index;
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        synchronized (this) {
            hasActiveHolder = true;
            this.notifyAll();
        }

        while (!hasActiveHolder) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                //Print something
            }
        }
        if (isStopActivity && !isDoingChangeSubtitle) {
            mediaPlayer.setDisplay(surfaceHolder);
            isStopActivity = false;
            if (videoPlayer_isPlaying)
                mediaPlayer.start();

            videoPlayer_isPlaying = false;
            return;
        }

        try {
            if (TextUtils.isEmpty(filePath))
                return;

            //filePath =getExternalSdCardPath() + "/Movie/video.mkv";
            //subTitlePath = Utilities.getExternalSdCardPath() + "/Movie/video1.srt";
            String subTitlePath = mediaFilesSubtitleList.get(currentVideoIndex);

            setFavoriteImage(filePath);

            mediaPlayer.reset();
            mediaPlayer = new MediaPlayer();
            //mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.setDisplay(surfaceHolder);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    try {
                        prepareViews(videoPlayer_isPlaying);
                        isDoingChangeSubtitle = false;
                    }
                    catch (Exception e)
                    {
                        Crashlytics.log(Log.ERROR, TAG, "setOnPreparedListener->onPrepared");
                        Crashlytics.logException(e);
                    }
                }
            });

            if (subTitlePath != null && new File(subTitlePath).exists()) {
                mediaPlayer.addTimedTextSource(subTitlePath, MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBRIP);
                int textTrackIndex = findTrackIndexFor(mediaPlayer.getTrackInfo());
                if (textTrackIndex >= 0) {
                    mediaPlayer.selectTrack(textTrackIndex);
                } else {
                    Log.w("test", "Cannot find text track!");
                }

                mediaPlayer.setOnTimedTextListener(new MediaPlayer.OnTimedTextListener() {
                    @Override
                    public void onTimedText(final MediaPlayer mediaPlayer, final TimedText timedText) {
                        if (timedText != null) {
                            Log.d("test", "subtitle: " + timedText.getText());
                            txtSubtitle.setText(timedText.getText().replace("\u200F", " "));
                        } else {
                            txtSubtitle.setText("");
                        }
                    }
                });
            }

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mediaPlayer.start();
                    nextVideoPlay();
                }
            });

            mediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    DisplayMetrics displayMetrics = getDisplayMetrics();
                    mainVideoWidth = width;
                    mainVideoHeight = height;
                    if (width <= displayMetrics.widthPixels)
                        videoWidth = width;
                    else
                        videoWidth = displayMetrics.widthPixels;

                    if (height <= displayMetrics.heightPixels)
                        videoHeight = height;
                    else
                        videoHeight = displayMetrics.heightPixels;

                    if (!isFullScreen && videoWidth != 0 && videoHeight != 0) {
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) playerSurfaceView.getLayoutParams();
                        lp.width = videoWidth;
                        lp.height = videoHeight;
                        playerSurfaceView.setLayoutParams(lp);
                    } else {
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) playerSurfaceView.getLayoutParams();
                        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        playerSurfaceView.setLayoutParams(lp);
                    }
                }
            });

            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Crashlytics.log(Log.ERROR, TAG, "VideoPlayerActivity : Error in surfaceCreated");
            Crashlytics.logException(e);
            e.printStackTrace();
            Log.e(TAG, "setupVideoPlayer: " + e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized (this) {
            hasActiveHolder = false;

            synchronized (this) {
                this.notifyAll();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        isStopActivity = true;
        if (isDoingChangeSubtitle) {
            mediaPlayer_currentPosition = mediaPlayer.getCurrentPosition();
        }
        if (mediaPlayer != null) {
            videoPlayer_isPlaying = mediaPlayer.isPlaying();
            mediaPlayer.pause();
        }
        super.onStop();

    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy: ");
        if (timer != null) {
            timer.purge();
            timer.cancel();
        }

        super.onDestroy();
    }
}
