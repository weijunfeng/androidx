/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media2.player;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.DeniedByServerException;
import android.media.MediaDrm;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.view.Surface;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.media.AudioAttributesCompat;
import androidx.media2.common.FileMediaItem;
import androidx.media2.common.MediaItem;
import androidx.media2.common.SessionPlayer.TrackInfo;
import androidx.media2.common.SubtitleData;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * MediaPlayer2 class can be used to control playback of audio/video files and streams.
 *
 * <p>Topics covered here are:
 * <ol>
 * <li><a href="#PlayerStates">Player states</a>
 * <li><a href="#Invalid_States">Invalid method calls</a>
 * <li><a href="#Permissions">Permissions</a>
 * <li><a href="#callbacks">Callbacks</a>
 * </ol>
 *
 *
 * <h3 id="PlayerStates">Player states</h3>
 *
 * <p>The playback control of audio/video files is managed as a state machine.</p>
 * <p><div style="text-align:center;"><img src="../../../images/mediaplayer2_state_diagram.png"
 *         alt="MediaPlayer2 State diagram"
 *         border="0" /></div></p>
 * <p>The MediaPlayer2 object has five states:</p>
 * <ol>
 *     <li><p>{@link #PLAYER_STATE_IDLE}: MediaPlayer2 is in the <strong>Idle</strong>
 *         state after you create it using
 *         {@link #create(Context)}, or after calling {@link #reset()}.</p>
 *
 *         <p>While in this state, you should call
 *         {@link #setMediaItem(MediaItem) setMediaItem()}. It is a good
 *         programming practice to register an {@link EventCallback#onCallCompleted onCallCompleted}
 *         <a href="#callback">callback</a> and watch for {@link #CALL_STATUS_BAD_VALUE} and
 *         {@link #CALL_STATUS_ERROR_IO}, which might be caused by <code>setMediaItem</code>.
 *         </p>
 *
 *         <p>Calling {@link #prepare()} transfers a MediaPlayer2 object to
 *         the <strong>Prepared</strong> state. Note
 *         that {@link #prepare()} is asynchronous. When the preparation completes,
 *         If you register a {@link EventCallback#onInfo} <a href="#callback">callback</a>
 *         the player executes the callback
 *         with {@link #MEDIA_INFO_PREPARED} before transitioning to the
 *         <strong>Prepared</strong> state.</p>
 *         </li>
 *
 *     <li>{@link #PLAYER_STATE_PREPARED}: A MediaPlayer object must be in the
 *         <strong>Prepared</strong> state before playback can be started for the first time.
 *         While in this state, you can set player properties
 *         such as audio/sound volume and looping by invoking the corresponding set methods.
 *         Calling {@link #play()} transfers a MediaPlayer2 object to
 *         the <strong>Playing</strong> state.
 *      </li>
 *
 *     <li>{@link #PLAYER_STATE_PLAYING}:
 *         <p>The player plays the media item while in this state.
 *         If you register an {@link EventCallback#onInfo} <a href="#callback">callback</a>
 *         the player regularly executes the callback with
 *         {@link #MEDIA_INFO_BUFFERING_UPDATE}.
 *         This allows applications to keep track of the buffering status
 *         while streaming audio/video.</p>
 *
 *         <p> When the playback reaches the end of stream, the behavior depends on whether or
 *         not you've enabled looping by calling {@link #loopCurrent(boolean)}:</p>
 *         <ul>
 *         <li>If the looping mode was set to <code>false</code> the player will transfer
 *         to the <strong>Paused</strong> state. If you registered an {@link EventCallback#onInfo}
 *         <a href="#callback">callback</a>
 *         the player calls the callback with {@link #MEDIA_INFO_DATA_SOURCE_END} before entering
 *         the <strong>Paused</strong> state.
 *         </li>
 *         <li>If the looping mode was set to <code>true</code>,
 *         the MediaPlayer2 object remains in the <strong>Playing</strong> state and replays its
 *         media item from the beginning.</li>
 *         </ul>
 *         </li>
 *
 *     <li>{@link #PLAYER_STATE_PAUSED}: Audio/video playback pauses while in this state.
 *         Call {@link #play()} to resume playback from the position where it paused.</li>
 *
 *     <li>{@link #PLAYER_STATE_ERROR}: <p>In general, playback might fail due to various
 *          reasons such as unsupported audio/video format, poorly interleaved
 *          audio/video, resolution too high, streaming timeout, and others.
 *          In addition, due to programming errors, a playback
 *          control operation might be performed from an <a href="#invalid_state">invalid state</a>.
 *          In these cases the player transitions to the <strong>Error</strong> state.</p>
 *
 *          <p>If you register an {@link EventCallback#onError}} <a href="#callback">callback</a>
 *          the callback will be performed when entering the state. When programming errors happen,
 *          such as calling {@link #prepare()} and {@link #setMediaItem(MediaItem)} methods
 *          from an <a href="#invalid_state">invalid state</a>, The callback is called with
 *          {@link #CALL_STATUS_INVALID_OPERATION} . The MediaPlayer2 object enters the
 *          <strong>Error</strong> whether or not a callback exists. </p>
 *
 *          <p>To recover from an error and reuse a MediaPlayer2 object that is in the <strong>
 *          Error</strong> state,
 *          call {@link #reset()}. The object will return to the <strong>Idle</strong>
 *          state and all state information will be lost.</p>
 *          </li>
 * </ol>
 *
 * <p>You should follow these best practices when coding an app that uses MediaPlayer2:</p>
 *
 * <ul>
 *
 * <li>Use <a href="#callback">callbacks</a> to respond to state changes and errors.</li>
 *
 * <li>When  a MediaPlayer2 object is no longer being used, call {@link #close()} as soon as
 * possible to release the resources used by the internal player engine associated with the
 * MediaPlayer2. Failure to call {@link #close()} may cause subsequent instances of MediaPlayer2
 * objects to fallback to software implementations or fail altogether. You cannot use MediaPlayer2
 * after you call {@link #close()}. There is no way to bring it back to any other state.</li>
 *
 * <li>The current playback position can be retrieved with a call to {@link #getCurrentPosition()},
 * which is helpful for applications such as a Music player that need to keep track of the playback
 * progress.</li>
 *
 * <li>The playback position can be adjusted with a call to {@link #seekTo}. Although the
 * asynchronous {@link #seekTo} call returns right away, the actual seek operation may take a
 * while to finish, especially for audio/video being streamed. If you register an
 * {@link EventCallback#onCallCompleted} <a href="#callback">callback</a>, the callback is
 * called When the seek operation completes with {@link #CALL_COMPLETED_SEEK_TO}.</li>
 *
 * <li>You can call {@link #seekTo(long, int)} from the <strong>Prepared</strong> and
 * <strong>Paused</strong> states. In these cases, if you are playing a video stream and
 * the requested position is valid  one video frame is displayed.</li>
 *
 * </ul>
 *
 * <h3 id="Invalid_States">Invalid method calls</h3>
 *
 * <p>The only methods you safely call from the <strong>Error</strong> state are {@link #close()},
 * {@link #reset()}, {@link #notifyWhenCommandLabelReached}, {@link #clearPendingCommands()},
 * {@link #setEventCallback}, {@link #clearEventCallback()} and {@link #getState()}.
 * Any other methods might throw an exception, return meaningless data, or invoke a
 * {@link EventCallback#onCallCompleted} with an error code.</p>
 *
 * <p>Most methods can be called from any non-Error state. They will either perform their work or
 * silently have no effect. The following table lists the methods that will invoke a
 * {@link EventCallback#onCallCompleted} with an error code or throw an exception when they are
 * called from the associated invalid states.</p>
 *
 * <table border="0" cellspacing="0" cellpadding="0">
 * <tr><th>Method Name</th>
 * <th>Invalid States</th></tr>
 *
 * <tr><td>setMediaItem</td> <td>{Prepared, Paused, Playing}</td></tr>
 * <tr><td>prepare</td> <td>{Prepared, Paused, Playing}</td></tr>
 * <tr><td>play</td> <td>{Idle}</td></tr>
 * <tr><td>pause</td> <td>{Idle}</td></tr>
 * <tr><td>seekTo</td> <td>{Idle}</td></tr>
 * <tr><td>getCurrentPosition</td> <td>{Idle}</td></tr>
 * <tr><td>getDuration</td> <td>{Idle}</td></tr>
 * <tr><td>getBufferedPosition</td> <td>{Idle}</td></tr>
 * <tr><td>getTracks</td> <td>{Idle}</td></tr>
 * <tr><td>getSelectedTrack</td> <td>{Idle}</td></tr>
 * <tr><td>selectTrack</td> <td>{Idle}</td></tr>
 * <tr><td>deselectTrack</td> <td>{Idle}</td></tr>
 * </table>
 *
 * <h3 id="Permissions">Permissions</h3>
 * <p>This class requires the {@link android.Manifest.permission#INTERNET} permission
 * when used with network-based content.
 *
 * <h3 id="callback">Callbacks</h3>
 * <p>Many errors do not result in a transition to the  <strong>Error</strong> state.
 * It is good programming practice to register callback listeners using
 * {@link #setEventCallback(Executor, EventCallback)} and
 * {@link #setDrmEventCallback(Executor, DrmEventCallback)}).
 * You can receive a callback at any time and from any state.</p>
 *
 * <p>If it's important for your app to respond to state changes (for instance, to update the
 * controls on a transport UI), you should register an {@link EventCallback#onCallCompleted} and
 * detect state change commands by testing the <code>what</code> parameter for a callback from one
 * of the state transition methods: {@link #CALL_COMPLETED_PREPARE}, {@link #CALL_COMPLETED_PLAY},
 * and {@link #CALL_COMPLETED_PAUSE}.
 * Then check the <code>status</code> parameter. The value {@link #CALL_STATUS_NO_ERROR} indicates a
 * successful transition. Any other value will be an error. Call {@link #getState()} to
 * determine the current state.</p>
 *
 * <p>In order for callbacks to work, your app must create
 * MediaPlayer2 objects on a thread that has its own running Looper. This can be done on the main UI
 * thread, which has a Looper.</p>
 */
/* package */ abstract class MediaPlayer2 {

    /**
     * Create a MediaPlayer2 object.
     *
     * @param context The context the player is running in
     * @return A MediaPlayer2 object created
     */
    @NonNull
    public static MediaPlayer2 create(@NonNull Context context) {
        return new ExoPlayerMediaPlayer2Impl(context);
    }

    protected MediaPlayer2() { }

    /**
     * Cancels the asynchronous call previously submitted.
     *
     * @param token the token which is returned from the asynchronous call.
     * @return {@code false} if the task could not be cancelled; {@code true} otherwise.
     */
    public abstract boolean cancel(Object token);

    /**
     * Releases the resources held by this {@code MediaPlayer2} object.
     *
     * It is considered good practice to call this method when you're
     * done using the MediaPlayer2. In particular, whenever an Activity
     * of an application is paused (its onPause() method is called),
     * or stopped (its onStop() method is called), this method should be
     * invoked to release the MediaPlayer2 object, unless the application
     * has a special need to keep the object around. In addition to
     * unnecessary resources (such as memory and instances of codecs)
     * being held, failure to call this method immediately if a
     * MediaPlayer2 object is no longer needed may also lead to
     * continuous battery consumption for mobile devices, and playback
     * failure for other applications if no multiple instances of the
     * same codec are supported on a device. Even if multiple instances
     * of the same codec are supported, some performance degradation
     * may be expected when unnecessary multiple instances are used
     * at the same time.
     */
    // This is a synchronous call.
    public abstract void close();

    /**
     * Starts or resumes playback. If playback had previously been paused,
     * playback will continue from where it was paused. If playback had
     * reached end of stream and been paused, or never started before,
     * playback will start at the beginning.
     *
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object play();

    /**
     * Prepares the player for playback, asynchronously.
     *
     * After setting the datasource and the display surface, you need to
     * call prepare().
     *
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object prepare();

    /**
     * Pauses playback. Call play() to resume.
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object pause();

    /**
     * Tries to play next media item if applicable.
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object skipToNext();

    /**
     * Moves the media to specified time position.
     * Same as {@link #seekTo(long, int)} with {@code mode = SEEK_PREVIOUS_SYNC}.
     *
     * @param msec the offset in milliseconds from the start to seek to
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public Object seekTo(long msec) {
        return seekTo(msec, SEEK_PREVIOUS_SYNC /* mode */);
    }

    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    public abstract long getCurrentPosition();

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds, if no duration is available
     *         (for example, if streaming live content), -1 is returned.
     */
    public abstract long getDuration();

    /**
     * Gets the current buffered media source position received through progressive downloading.
     * The received buffering percentage indicates how much of the content has been buffered
     * or played. For example a buffering update of 80 percent when half the content
     * has already been played indicates that the next 30 percent of the
     * content to play has been buffered.
     *
     * @return the current buffered media source position in milliseconds
     */
    public abstract long getBufferedPosition();

    /**
     * Gets the current MediaPlayer2 state.
     *
     * @return the current MediaPlayer2 state.
     */
    public abstract @MediaPlayer2State int getState();

    /**
     * Sets the audio attributes for this MediaPlayer2.
     * See {@link AudioAttributesCompat} for how to build and configure an instance of this class.
     * You must call this method before {@link #prepare()} in order
     * for the audio attributes to become effective thereafter.
     * @param attributes a non-null set of audio attributes
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object setAudioAttributes(@NonNull AudioAttributesCompat attributes);

    /**
     * Gets the audio attributes for this MediaPlayer2.
     * @return attributes a set of audio attributes
     */
    public abstract @Nullable AudioAttributesCompat getAudioAttributes();

    /**
     * Sets the media item as described by a MediaItem.
     * <p>
     * When the media item is a {@link FileMediaItem}, the {@link ParcelFileDescriptor}
     * in the {@link FileMediaItem} will be closed by the player.
     *
     * @param item the descriptor of media item you want to play
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object setMediaItem(@NonNull MediaItem item);

    /**
     * Sets a single media item as described by a MediaItem which will be played
     * after current media item is finished.
     * <p>
     * When the media item is a {@link FileMediaItem}, the {@link ParcelFileDescriptor}
     * in the {@link FileMediaItem} will be closed by the player.
     *
     * @param item the descriptor of media item you want to play after current one
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object setNextMediaItem(@NonNull MediaItem item);

    /**
     * Sets a list of media items to be played sequentially after current media item is done.
     * <p>
     * If a media item in the list is a {@link FileMediaItem}, the {@link ParcelFileDescriptor}
     * in the {@link FileMediaItem} will be closed by the player.
     *
     * @param items the list of media items you want to play after current one
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object setNextMediaItems(@NonNull List<MediaItem> items);

    /**
     * Gets the current media item as described by a MediaItem.
     *
     * @return the current MediaItem
     */
    public abstract @Nullable MediaItem getCurrentMediaItem();

    /**
     * Configures the player to loop on the current media item.
     * @param loop true if the current media item is meant to loop.
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object loopCurrent(boolean loop);

    /**
     * Sets the volume of the audio of the media to play, expressed as a linear multiplier
     * on the audio samples.
     * Note that this volume is specific to the player, and is separate from stream volume
     * used across the platform.<br>
     * A value of 0.0f indicates muting, a value of 1.0f is the nominal unattenuated and unamplified
     * gain. See {@link #getMaxPlayerVolume()} for the volume range supported by this player.
     * @param volume a value between 0.0f and {@link #getMaxPlayerVolume()}.
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object setPlayerVolume(float volume);

    /**
     * Returns the current volume of this player to this player.
     * Note that it does not take into account the associated stream volume.
     * @return the player volume.
     */
    public abstract float getPlayerVolume();

    /**
     * @return the maximum volume that can be used in {@link #setPlayerVolume(float)}.
     */
    public float getMaxPlayerVolume() {
        return 1.0f;
    }

    /**
     * Insert a task in the command queue to help the client to identify whether a batch
     * of commands has been finished. When this command is processed, a notification
     * {@link EventCallback#onCommandLabelReached} will be fired with the
     * given {@code label}.
     *
     * @see EventCallback#onCommandLabelReached
     *
     * @param label An application specific Object used to help to identify the completeness
     * of a batch of commands.
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object notifyWhenCommandLabelReached(@NonNull Object label);

    /**
     * Sets the {@link Surface} to be used as the sink for the video portion of
     * the media.  Setting a
     * Surface will un-set any Surface or SurfaceHolder that was previously set.
     * A null surface will result in only the audio track being played.
     *
     * If the Surface sends frames to a {@link SurfaceTexture}, the timestamps
     * returned from {@link SurfaceTexture#getTimestamp()} will have an
     * unspecified zero point.  These timestamps cannot be directly compared
     * between different media sources, different instances of the same media
     * source, or multiple runs of the same program.  The timestamp is normally
     * monotonically increasing and is unaffected by time-of-day adjustments,
     * but it is reset when the position is set.
     *
     * @param surface The {@link Surface} to be used for the video portion of
     * the media.
     * @throws IllegalStateException if the internal player engine has not been
     * initialized or has been released.
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object setSurface(@Nullable Surface surface);

    /* Do not change these video scaling mode values below without updating
     * their counterparts in system/window.h! Please do not forget to update
     * {@link #isVideoScalingModeSupported} when new video scaling modes
     * are added.
     */
    /**
     * Specifies a video scaling mode. The content is stretched to the
     * surface rendering area. When the surface has the same aspect ratio
     * as the content, the aspect ratio of the content is maintained;
     * otherwise, the aspect ratio of the content is not maintained when video
     * is being rendered.
     * There is no content cropping with this video scaling mode.
     */
    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT = 1;

    /**
     * Discards all pending commands.
     */
    // This is a synchronous call.
    public abstract void clearPendingCommands();

    /**
     * Returns the width of the video.
     *
     * @return the width of the video, or 0 if there is no video or the width has not been
     * determined yet. The {@link EventCallback} can be registered via
     * {@link #setEventCallback(Executor, EventCallback)} to provide a
     * notification {@link EventCallback#onVideoSizeChanged} when the width
     * is available.
     */
    public abstract int getVideoWidth();

    /**
     * Returns the height of the video.
     *
     * @return the height of the video, or 0 if there is no video or the height has not been
     * determined yet. The {@link EventCallback} can be registered via
     * {@link #setEventCallback(Executor, EventCallback)} to provide a
     * notification {@link EventCallback#onVideoSizeChanged} when the height is
     * available.
     */
    public abstract int getVideoHeight();

    /**
     * Return Metrics data about the current player.
     *
     * @return a {@link PersistableBundle} containing the set of attributes and values
     * available for the media being handled by this instance of MediaPlayer2
     * The attributes are descibed in {@link MetricsConstants}.
     *
     *  Additional vendor-specific fields may also be present in
     *  the return value.
     */
    @RequiresApi(21)
    public abstract PersistableBundle getMetrics();

    /**
     * Sets playback rate using {@link PlaybackParams}. The player sets its internal
     * PlaybackParams to the given input. This does not change the player state. For example,
     * if this is called with the speed of 2.0f in {@link #PLAYER_STATE_PAUSED}, the player will
     * just update internal property and stay paused. Once the client calls {@link #play()}
     * afterwards, the player will start playback with the given speed. Calling this with zero
     * speed is not allowed.
     *
     * @param params the playback params.
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object setPlaybackParams(@NonNull PlaybackParams params);

    /**
     * Gets the playback params, containing the current playback rate.
     *
     * @return the playback params.
     */
    @NonNull
    public abstract PlaybackParams getPlaybackParams();

    /**
     * Seek modes used in method seekTo(long, int) to move media position
     * to a specified location.
     *
     * Do not change these mode values without updating their counterparts
     * in include/media/IMediaSource.h!
     */
    /**
     * This mode is used with {@link #seekTo(long, int)} to move media position to
     * a sync (or key) frame associated with a media item that is located
     * right before or at the given time.
     *
     * @see #seekTo(long, int)
     */
    public static final int SEEK_PREVIOUS_SYNC    = 0x00;
    /**
     * This mode is used with {@link #seekTo(long, int)} to move media position to
     * a sync (or key) frame associated with a media item that is located
     * right after or at the given time.
     *
     * @see #seekTo(long, int)
     */
    public static final int SEEK_NEXT_SYNC        = 0x01;
    /**
     * This mode is used with {@link #seekTo(long, int)} to move media position to
     * a sync (or key) frame associated with a media item that is located
     * closest to (in time) or at the given time.
     *
     * @see #seekTo(long, int)
     */
    public static final int SEEK_CLOSEST_SYNC     = 0x02;
    /**
     * This mode is used with {@link #seekTo(long, int)} to move media position to
     * a frame (not necessarily a key frame) associated with a media item that
     * is located closest to or at the given time.
     *
     * @see #seekTo(long, int)
     */
    public static final int SEEK_CLOSEST          = 0x03;

    @RestrictTo(LIBRARY)
    @IntDef(flag = false, /*prefix = "SEEK",*/ value = {
            SEEK_PREVIOUS_SYNC,
            SEEK_NEXT_SYNC,
            SEEK_CLOSEST_SYNC,
            SEEK_CLOSEST,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SeekMode {}

    /**
     * Moves the media to specified time position by considering the given mode.
     * <p>
     * When seekTo is finished, the user will be notified via
     * {@link EventCallback#onInfo} with {@link #CALL_COMPLETED_SEEK_TO}.
     * There is at most one active seekTo processed at any time. If there is a to-be-completed
     * seekTo, new seekTo requests will be queued in such a way that only the last request
     * is kept. When current seekTo is completed, the queued request will be processed if
     * that request is different from just-finished seekTo operation, i.e., the requested
     * position or mode is different.
     *
     * @param msec the offset in milliseconds from the start to seek to.
     * When seeking to the given time position, there is no guarantee that the media item
     * has a frame located at the position. When this happens, a frame nearby will be rendered.
     * If msec is negative, time position zero will be used.
     * If msec is larger than duration, duration will be used.
     * @param mode the mode indicating where exactly to seek to.
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object seekTo(long msec, @SeekMode int mode);

    /**
     * Gets current playback position as a {@link MediaTimestamp}.
     * <p>
     * The MediaTimestamp represents how the media time correlates to the system time in
     * a linear fashion using an anchor and a clock rate. During regular playback, the media
     * time moves fairly constantly (though the anchor frame may be rebased to a current
     * system time, the linear correlation stays steady). Therefore, this method does not
     * need to be called often.
     * <p>
     * To help users get current playback position, this method always anchors the timestamp
     * to the current {@link System#nanoTime system time}, so
     * {@link MediaTimestamp#getAnchorMediaTimeUs} can be used as current playback position.
     *
     * @return a MediaTimestamp object if a timestamp is available, or {@code null} if no timestamp
     *         is available, e.g. because the media player has not been initialized.
     *
     * @see MediaTimestamp
     */
    @Nullable
    public abstract MediaTimestamp getTimestamp();

    /**
     * Resets the MediaPlayer2 to its uninitialized state. After calling
     * this method, you will have to initialize it again by setting the
     * media item and calling prepare().
     */
    // This is a synchronous call.
    public abstract void reset();

    /**
     * Sets the audio session ID.
     *
     * @param sessionId the audio session ID.
     * The audio session ID is a system wide unique identifier for the audio stream played by
     * this MediaPlayer2 instance.
     * The primary use of the audio session ID  is to associate audio effects to a particular
     * instance of MediaPlayer2: if an audio session ID is provided when creating an audio effect,
     * this effect will be applied only to the audio content of media players within the same
     * audio session and not to the output mix.
     * When created, a MediaPlayer2 instance automatically generates its own audio session ID.
     * However, it is possible to force this player to be part of an already existing audio session
     * by calling this method.
     * This method must be called before one of the overloaded <code> setMediaItem </code> methods.
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object setAudioSessionId(int sessionId);

    /**
     * Returns the audio session ID.
     *
     * @return the audio session ID. {@see #setAudioSessionId(int)}
     * Note that the audio session ID is 0 only if a problem occurred when the MediaPlayer2 was
     * constructed.
     */
    public abstract int getAudioSessionId();

    /**
     * Attaches an auxiliary effect to the player. A typical auxiliary effect is a reverberation
     * effect which can be applied on any sound source that directs a certain amount of its
     * energy to this effect. This amount is defined by setAuxEffectSendLevel().
     * See {@link #setAuxEffectSendLevel(float)}.
     * <p>After creating an auxiliary effect (e.g.
     * {@link android.media.audiofx.EnvironmentalReverb}), retrieve its ID with
     * {@link android.media.audiofx.AudioEffect#getId()} and use it when calling this method
     * to attach the player to the effect.
     * <p>To detach the effect from the player, call this method with a null effect id.
     * <p>This method must be called after one of the overloaded <code> setMediaItem </code>
     * methods.
     * @param effectId system wide unique id of the effect to attach
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object attachAuxEffect(int effectId);


    /**
     * Sets the send level of the player to the attached auxiliary effect.
     * See {@link #attachAuxEffect(int)}. The level value range is 0 to 1.0.
     * <p>By default the send level is 0, so even if an effect is attached to the player
     * this method must be called for the effect to be applied.
     * <p>Note that the passed level value is a raw scalar. UI controls should be scaled
     * logarithmically: the gain applied by audio framework ranges from -72dB to 0dB,
     * so an appropriate conversion from linear UI input x to level is:
     * x == 0 -> level = 0
     * 0 < x <= R -> level = 10^(72*(x-R)/20/R)
     * @param level send level scalar
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object setAuxEffectSendLevel(float level);

    /**
     * Returns a List of track information.
     *
     * @return List of track info. The total number of tracks is the array length.
     */
    @NonNull
    public abstract List<TrackInfo> getTracks();

    /**
     * Returns the metadata of the audio, video, or subtitle track currently selected for playback,
     * The return value is an item of the array returned by {@link #getTracks()}.
     *
     * @param trackType should be one of {@link TrackInfo#MEDIA_TRACK_TYPE_VIDEO},
     * {@link TrackInfo#MEDIA_TRACK_TYPE_AUDIO}, or
     * {@link TrackInfo#MEDIA_TRACK_TYPE_SUBTITLE}
     * @return metadata of the audio, video, or subtitle track currently selected for playback;
     * {@code null} is returned when there is no selected track for {@code trackType} or
     * when {@code trackType} is not one of audio, video, or subtitle.
     * @throws IllegalStateException if called after {@link #close()}
     *
     * @see #getTracks()
     */
    @Nullable
    public abstract TrackInfo getSelectedTrack(int trackType);

    /**
     * Selects a track.
     * <p>
     * If a MediaPlayer2 is in invalid state, {@link #CALL_STATUS_INVALID_OPERATION} will be
     * reported with {@link EventCallback#onCallCompleted}.
     * If a MediaPlayer2 is in <em>Playing</em> state, the selected track is presented immediately.
     * If a MediaPlayer2 is not in Started state, it just marks the track to be played.
     * </p>
     * <p>
     * In any valid state, if it is called multiple times on the same type of track (ie. Video,
     * Audio, Subtitle), the most recent one will be chosen.
     * </p>
     * <p>
     * The first audio and video tracks are selected by default if available, even though
     * this method is not called. However, no subtitle track will be selected until
     * this function is called.
     * </p>
     * <p>
     * Currently, only subtitle tracks or audio tracks can be selected via this method.
     * </p>
     * @param trackId the id of the track to be selected. The id can be obtained by calling
     * {@link TrackInfo#getId()} to an {@link TrackInfo} returned by {@link #getTracks()}.
     * Note that the {@link TrackInfo}s may become invalid when
     * {@link EventCallback#onTracksChanged} is called.
     *
     * @see TrackInfo#getId()
     * @see #getTracks
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    @NonNull
    public abstract Object selectTrack(int trackId);

    /**
     * Deselects a track.
     * <p>
     * Currently, the track must be a subtitle track and no audio or video tracks can be
     * deselected. If the subtitle track identified by index has not been
     * selected before, it throws an exception.
     * </p>
     * @param trackId the id of the track to be deselected. The id can be obtained by calling
     * {@link TrackInfo#getId()} to an {@link TrackInfo} returned by {@link #getTracks()} or
     * {@link #getSelectedTrack(int)}. Note that the {@link TrackInfo}s may become invalid when
     * {@link EventCallback#onTracksChanged} is called.
     *
     * @see TrackInfo#getId()
     * @see #getTracks
     * @see #getSelectedTrack(int)
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    @NonNull
    public abstract Object deselectTrack(int trackId);

    /**
     * Interface definition for callbacks to be invoked when the player has the corresponding
     * events.
     */
    public abstract static class EventCallback {
        /**
         * Called to indicate the video size
         *
         * The video size (width and height) could be 0 if there was no video,
         * no display surface was set, or the value was not determined yet.
         *
         * @param mp the MediaPlayer2 associated with this callback
         * @param item the MediaItem of this media item
         * @param width the width of the video
         * @param height the height of the video
         */
        public void onVideoSizeChanged(
                MediaPlayer2 mp, MediaItem item, int width, int height) { }

        /**
         * Called to indicate available timed metadata
         * <p>
         * This method will be called as timed metadata is extracted from the media,
         * in the same order as it occurs in the media. The timing of this event is
         * not controlled by the associated timestamp.
         * <p>
         * Currently only HTTP live streaming data URI's embedded with timed ID3 tags generates
         * {@link TimedMetaData}.
         *
         * @see MediaPlayer2#selectTrack(int)
         * @see TimedMetaData
         *
         * @param mp the MediaPlayer2 associated with this callback
         * @param item the MediaItem of this media item
         * @param data the timed metadata sample associated with this event
         */
        public void onTimedMetaDataAvailable(
                MediaPlayer2 mp, MediaItem item, TimedMetaData data) { }

        /**
         * Called to indicate an error.
         *
         * @param mp the MediaPlayer2 the error pertains to
         * @param item the MediaItem of this media item
         * @param what the type of error that has occurred.
         * @param extra an extra code, specific to the error. Typically
         * implementation dependent.
         */
        public void onError(
                MediaPlayer2 mp, MediaItem item, @MediaError int what, int extra) { }

        /**
         * Called to indicate an info or a warning.
         *
         * @param mp the MediaPlayer2 the info pertains to.
         * @param item the MediaItem of this media item
         * @param what the type of info or warning.
         * @param extra an extra code, specific to the info. Typically
         * implementation dependent.
         */
        public void onInfo(MediaPlayer2 mp, MediaItem item, @MediaInfo int what, int extra) { }

        /**
         * Called to acknowledge an API call.
         *
         * @param mp the MediaPlayer2 the call was made on.
         * @param item the MediaItem of this media item
         * @param what the enum for the API call.
         * @param status the returned status code for the call.
         */
        public void onCallCompleted(
                MediaPlayer2 mp, MediaItem item, @CallCompleted int what,
                @CallStatus int status) { }

        /**
         * Called when a discontinuity in the normal progression of the media time is detected.
         * The "normal progression" of media time is defined as the expected increase of the
         * playback position when playing media, relative to the playback speed (for instance every
         * second, media time increases by two seconds when playing at 2x).<br>
         * Discontinuities are encountered in the following cases:
         * <ul>
         * <li>when the player is starved for data and cannot play anymore</li>
         * <li>when the player encounters a playback error</li>
         * <li>when the a seek operation starts, and when it's completed</li>
         * <li>when the playback speed changes</li>
         * <li>when the playback state changes</li>
         * <li>when the player is reset</li>
         * </ul>
         *
         * @param mp the MediaPlayer2 the media time pertains to.
         * @param item the MediaItem of this media item
         * @param timestamp the timestamp that correlates media time, system time and clock rate,
         *     or {@link MediaTimestamp#TIMESTAMP_UNKNOWN} in an error case.
         */
        public void onMediaTimeDiscontinuity(
                MediaPlayer2 mp, MediaItem item, MediaTimestamp timestamp) { }

        /**
         * Called to indicate {@link #notifyWhenCommandLabelReached(Object)} has been processed.
         *
         * @param mp the MediaPlayer2 {@link #notifyWhenCommandLabelReached(Object)} was called on.
         * @param label the application specific Object given by
         *        {@link #notifyWhenCommandLabelReached(Object)}.
         */
        public void onCommandLabelReached(MediaPlayer2 mp, @NonNull Object label) { }

        /**
         * Called when when a player subtitle track has new subtitle data available.
         * @param mp the player that reports the new subtitle data
         * @param item the MediaItem of this media item
         * @param track the track that has the subtitle data
         * @param data the subtitle data
         */
        public void onSubtitleData(@NonNull MediaPlayer2 mp, @NonNull MediaItem item,
                @NonNull TrackInfo track, @NonNull SubtitleData data) { }

        /**
         * Called when the tracks of the current media item is changed such as
         * 1) when tracks of a media item become available, or
         * 2) when new tracks are found during playback.
         * <p>
         * When it's called, the previous tracks may be invalidated so it's recommended to use the
         * most recent tracks to call {@link #selectTrack} or {@link #deselectTrack}.
         *
         * @param mp the player associated with this callback
         * @param tracks the list of tracks. It can be empty.
         */
        public void onTracksChanged(@NonNull MediaPlayer2 mp,
                @NonNull List<TrackInfo> tracks) { }
    }

    /**
     * Sets the callback to be invoked when the media source is ready for playback.
     *
     * @param eventCallback the callback that will be run
     * @param executor the executor through which the callback should be invoked
     */
    // This is a synchronous call.
    public abstract void setEventCallback(
            @NonNull Executor executor, @NonNull EventCallback eventCallback);

    /**
     * Clears the {@link EventCallback}.
     */
    // This is a synchronous call.
    public abstract void clearEventCallback();

    /**
     * MediaPlayer2 has not been prepared or just has been reset.
     * In this state, MediaPlayer2 doesn't fetch data.
     */
    public static final int PLAYER_STATE_IDLE = 1001;

    /**
     * MediaPlayer2 has been just prepared.
     * In this state, MediaPlayer2 just fetches data from media source,
     * but doesn't actively render data.
     */
    public static final int PLAYER_STATE_PREPARED = 1002;

    /**
     * MediaPlayer2 is paused.
     * In this state, MediaPlayer2 doesn't actively render data.
     */
    public static final int PLAYER_STATE_PAUSED = 1003;

    /**
     * MediaPlayer2 is actively playing back data.
     */
    public static final int PLAYER_STATE_PLAYING = 1004;

    /**
     * MediaPlayer2 has hit some fatal error and cannot continue playback.
     */
    public static final int PLAYER_STATE_ERROR = 1005;

    @RestrictTo(LIBRARY)
    @IntDef(flag = false, value = {
            PLAYER_STATE_IDLE,
            PLAYER_STATE_PREPARED,
            PLAYER_STATE_PAUSED,
            PLAYER_STATE_PLAYING,
            PLAYER_STATE_ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MediaPlayer2State {}

    /**
     * Unspecified media player error.
     * @see EventCallback#onError
     */
    public static final int MEDIA_ERROR_UNKNOWN = 1;

    /**
     * File or network related operation errors.
     * @see EventCallback#onError
     */
    public static final int MEDIA_ERROR_IO = -1004;

    /**
     * Bitstream is not conforming to the related coding standard or file spec.
     * @see EventCallback#onError
     */
    public static final int MEDIA_ERROR_MALFORMED = -1007;
    /**
     * Bitstream is conforming to the related coding standard or file spec, but
     * the media framework does not support the feature.
     * @see EventCallback#onError
     */
    public static final int MEDIA_ERROR_UNSUPPORTED = -1010;
    /**
     * Some operation takes too long to complete, usually more than 3-5 seconds.
     * @see EventCallback#onError
     */
    public static final int MEDIA_ERROR_TIMED_OUT = -110;

    /**
     * Unspecified low-level system error. This value originated from UNKNOWN_ERROR in
     * system/core/include/utils/Errors.h
     * @see EventCallback#onError
     */
    public static final int MEDIA_ERROR_SYSTEM = -2147483648;

    /**
     */
    @RestrictTo(LIBRARY)
    @IntDef(flag = false, /*prefix = "MEDIA_ERROR",*/ value = {
            MEDIA_ERROR_UNKNOWN,
            MEDIA_ERROR_IO,
            MEDIA_ERROR_MALFORMED,
            MEDIA_ERROR_UNSUPPORTED,
            MEDIA_ERROR_TIMED_OUT,
            MEDIA_ERROR_SYSTEM
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MediaError {}

    /**
     * Unspecified media player info.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_UNKNOWN = 1;

    /**
     * The player just started the playback of this media item.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_DATA_SOURCE_START = 2;

    /**
     * The player just pushed the very first video frame for rendering.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_VIDEO_RENDERING_START = 3;

    /**
     * The player just rendered the very first audio sample.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_AUDIO_RENDERING_START = 4;

    /**
     * The player just completed the playback of this media item.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_DATA_SOURCE_END = 5;

    /**
     * The player just completed the playback of all the media items set by {@link #setMediaItem},
     * {@link #setNextMediaItem} and {@link #setNextMediaItems}.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_DATA_SOURCE_LIST_END = 6;

    /**
     * The player just completed an iteration of playback loop. This event is sent only when
     * looping is enabled by {@link #loopCurrent}.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_DATA_SOURCE_REPEAT = 7;

    /**
     * The player just prepared a media item.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_PREPARED = 100;

    /**
     * The video is too complex for the decoder: it can't decode frames fast
     * enough. Possibly only the audio plays fine at this stage.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_VIDEO_TRACK_LAGGING = 700;

    /**
     * MediaPlayer2 is temporarily pausing playback internally in order to
     * buffer more data.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_BUFFERING_START = 701;

    /**
     * MediaPlayer2 is resuming playback after filling buffers.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_BUFFERING_END = 702;

    /**
     * Estimated network bandwidth information (kbps) is available; currently this event fires
     * simultaneously as {@link #MEDIA_INFO_BUFFERING_START} and {@link #MEDIA_INFO_BUFFERING_END}
     * when playing network files.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_NETWORK_BANDWIDTH = 703;

    /**
     * Update status in buffering a media source received through progressive downloading.
     * The received buffering percentage indicates how much of the content has been buffered
     * or played. For example a buffering update of 80 percent when half the content
     * has already been played indicates that the next 30 percent of the
     * content to play has been buffered.
     *
     * The {@code extra} parameter in {@link EventCallback#onInfo} is the
     * percentage (0-100) of the content that has been buffered or played thus far.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_BUFFERING_UPDATE = 704;

    /**
     * Bad interleaving means that a media has been improperly interleaved or
     * not interleaved at all, e.g has all the video samples first then all the
     * audio ones. Video is playing but a lot of disk seeks may be happening.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_BAD_INTERLEAVING = 800;

    /**
     * The media cannot be seeked (e.g live stream)
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_NOT_SEEKABLE = 801;

    /**
     * A new set of metadata is available.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_METADATA_UPDATE = 802;

    /**
     * A new set of external-only metadata is available.  Used by
     * JAVA framework to avoid triggering track scanning.
     */
    public static final int MEDIA_INFO_EXTERNAL_METADATA_UPDATE = 803;

    /**
     * Informs that audio is not playing. Note that playback of the video
     * is not interrupted.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_AUDIO_NOT_PLAYING = 804;

    /**
     * Informs that video is not playing. Note that playback of the audio
     * is not interrupted.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_VIDEO_NOT_PLAYING = 805;

    /**
     * Subtitle track was not supported by the media framework.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_UNSUPPORTED_SUBTITLE = 901;

    /**
     * Reading the subtitle track takes too long.
     * @see EventCallback#onInfo
     */
    public static final int MEDIA_INFO_SUBTITLE_TIMED_OUT = 902;

    /**
     */
    @RestrictTo(LIBRARY)
    @IntDef(flag = false, /*prefix = "MEDIA_INFO",*/ value = {
            MEDIA_INFO_UNKNOWN,
            MEDIA_INFO_DATA_SOURCE_START,
            MEDIA_INFO_VIDEO_RENDERING_START,
            MEDIA_INFO_AUDIO_RENDERING_START,
            MEDIA_INFO_DATA_SOURCE_END,
            MEDIA_INFO_DATA_SOURCE_LIST_END,
            MEDIA_INFO_DATA_SOURCE_REPEAT,
            MEDIA_INFO_PREPARED,
            MEDIA_INFO_VIDEO_TRACK_LAGGING,
            MEDIA_INFO_BUFFERING_START,
            MEDIA_INFO_BUFFERING_END,
            MEDIA_INFO_NETWORK_BANDWIDTH,
            MEDIA_INFO_BUFFERING_UPDATE,
            MEDIA_INFO_BAD_INTERLEAVING,
            MEDIA_INFO_NOT_SEEKABLE,
            MEDIA_INFO_METADATA_UPDATE,
            MEDIA_INFO_EXTERNAL_METADATA_UPDATE,
            MEDIA_INFO_AUDIO_NOT_PLAYING,
            MEDIA_INFO_VIDEO_NOT_PLAYING,
            MEDIA_INFO_UNSUPPORTED_SUBTITLE,
            MEDIA_INFO_SUBTITLE_TIMED_OUT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MediaInfo {}

    //--------------------------------------------------------------------------
    /**
     * The player just completed a call {@link #attachAuxEffect}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_ATTACH_AUX_EFFECT = 1;

    /**
     * The player just completed a call {@link #deselectTrack}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_DESELECT_TRACK = 2;

    /**
     * The player just completed a call {@link #loopCurrent}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_LOOP_CURRENT = 3;

    /**
     * The player just completed a call {@link #pause}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_PAUSE = 4;

    /**
     * The player just completed a call {@link #play}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_PLAY = 5;

    /**
     * The player just completed a call {@link #prepare}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_PREPARE = 6;

    /**
     * The player just completed a call {@link #seekTo}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SEEK_TO = 14;

    /**
     * The player just completed a call {@link #selectTrack}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SELECT_TRACK = 15;

    /**
     * The player just completed a call {@link #setAudioAttributes}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SET_AUDIO_ATTRIBUTES = 16;

    /**
     * The player just completed a call {@link #setAudioSessionId}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SET_AUDIO_SESSION_ID = 17;

    /**
     * The player just completed a call {@link #setAuxEffectSendLevel}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SET_AUX_EFFECT_SEND_LEVEL = 18;

    /**
     * The player just completed a call {@link #setMediaItem}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SET_DATA_SOURCE = 19;

    /**
     * The player just completed a call {@link #setNextMediaItem}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SET_NEXT_DATA_SOURCE = 22;

    /**
     * The player just completed a call {@link #setNextMediaItems}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SET_NEXT_DATA_SOURCES = 23;

    /**
     * The player just completed a call {@link #setPlaybackParams}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SET_PLAYBACK_PARAMS = 24;

    /**
     * The player just completed a call {@link #setPlayerVolume}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SET_PLAYER_VOLUME = 26;

    /**
     * The player just completed a call {@link #setSurface}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SET_SURFACE = 27;

    /**
     * The player just completed a call {@link #skipToNext}.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_COMPLETED_SKIP_TO_NEXT = 29;

    /**
     * The start of the methods which have separate call complete callback.
     */
    public static final int SEPARATE_CALL_COMPLETE_CALLBACK_START = 1000;

    /**
     * The player just completed a call {@code notifyWhenCommandLabelReached}.
     * @see EventCallback#onCommandLabelReached
     */
    public static final int CALL_COMPLETED_NOTIFY_WHEN_COMMAND_LABEL_REACHED =
            SEPARATE_CALL_COMPLETE_CALLBACK_START;

    /**
     * The player just completed a call {@link #prepareDrm}.
     * @see EventCallback#onCommandLabelReached
     */
    public static final int CALL_COMPLETED_PREPARE_DRM =
            SEPARATE_CALL_COMPLETE_CALLBACK_START + 1;

    /**
     */
    @RestrictTo(LIBRARY)
    @IntDef(flag = false, /*prefix = "CALL_COMPLETED",*/ value = {
            CALL_COMPLETED_ATTACH_AUX_EFFECT,
            CALL_COMPLETED_DESELECT_TRACK,
            CALL_COMPLETED_LOOP_CURRENT,
            CALL_COMPLETED_PAUSE,
            CALL_COMPLETED_PLAY,
            CALL_COMPLETED_PREPARE,
            CALL_COMPLETED_SEEK_TO,
            CALL_COMPLETED_SELECT_TRACK,
            CALL_COMPLETED_SET_AUDIO_ATTRIBUTES,
            CALL_COMPLETED_SET_AUDIO_SESSION_ID,
            CALL_COMPLETED_SET_AUX_EFFECT_SEND_LEVEL,
            CALL_COMPLETED_SET_DATA_SOURCE,
            CALL_COMPLETED_SET_NEXT_DATA_SOURCE,
            CALL_COMPLETED_SET_NEXT_DATA_SOURCES,
            CALL_COMPLETED_SET_PLAYBACK_PARAMS,
            CALL_COMPLETED_SET_PLAYER_VOLUME,
            CALL_COMPLETED_SET_SURFACE,
            CALL_COMPLETED_SKIP_TO_NEXT,
            CALL_COMPLETED_NOTIFY_WHEN_COMMAND_LABEL_REACHED,
            CALL_COMPLETED_PREPARE_DRM,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CallCompleted {}

    /**
     * Status code represents that call is completed without an error.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_STATUS_NO_ERROR = 0;

    /**
     * Status code represents that call is ended with an unknown error.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_STATUS_ERROR_UNKNOWN = Integer.MIN_VALUE;

    /**
     * Status code represents that the player is not in valid state for the operation.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_STATUS_INVALID_OPERATION = 1;

    /**
     * Status code represents that the argument is illegal.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_STATUS_BAD_VALUE = 2;

    /**
     * Status code represents that the operation is not allowed.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_STATUS_PERMISSION_DENIED = 3;

    /**
     * Status code represents a file or network related operation error.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_STATUS_ERROR_IO = 4;

    /**
     * Status code represents that the player skipped the call. For example, a {@link #seekTo}
     * request may be skipped if it is followed by another {@link #seekTo} request.
     * @see EventCallback#onCallCompleted
     */
    public static final int CALL_STATUS_SKIPPED = 5;

    /**
     */
    @RestrictTo(LIBRARY)
    @IntDef(flag = false, /*prefix = "CALL_STATUS",*/ value = {
            CALL_STATUS_NO_ERROR,
            CALL_STATUS_ERROR_UNKNOWN,
            CALL_STATUS_INVALID_OPERATION,
            CALL_STATUS_BAD_VALUE,
            CALL_STATUS_PERMISSION_DENIED,
            CALL_STATUS_ERROR_IO,
            CALL_STATUS_SKIPPED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CallStatus {}

    // Modular DRM begin

    /**
     * Interface definition of a callback to be invoked when the app
     * can do DRM configuration (get/set properties) before the session
     * is opened. This facilitates configuration of the properties, like
     * 'securityLevel', which has to be set after DRM scheme creation but
     * before the DRM session is opened.
     *
     * The only allowed DRM calls in this listener are {@link #getDrmPropertyString}
     * and {@link #setDrmPropertyString}.
     */
    public interface OnDrmConfigHelper {
        /**
         * Called to give the app the opportunity to configure DRM before the session is created
         *
         * @param mp the {@code MediaPlayer2} associated with this callback
         * @param item the MediaItem of this media item
         */
        void onDrmConfig(MediaPlayer2 mp, MediaItem item);
    }

    /**
     * Register a callback to be invoked for configuration of the DRM object before
     * the session is created.
     * The callback will be invoked synchronously during the execution
     * of {@link #prepareDrm(UUID uuid)}.
     *
     * @param listener the callback that will be run
     */
    // This is a synchronous call.
    public abstract void setOnDrmConfigHelper(OnDrmConfigHelper listener);

    /**
     * Interface definition for callbacks to be invoked when the player has the corresponding
     * DRM events.
     */
    public abstract static class DrmEventCallback {
        /**
         * Called to indicate DRM info is available
         *
         * @param mp the {@code MediaPlayer2} associated with this callback
         * @param item the MediaItem of this media item
         * @param drmInfo DRM info of the source including PSSH, and subset
         *                of crypto schemes supported by this device
         */
        public void onDrmInfo(MediaPlayer2 mp, MediaItem item, DrmInfo drmInfo) { }

        /**
         * Called to notify the client that {@link #prepareDrm} is finished and ready for
         * key request/response.
         *
         * @param mp the {@code MediaPlayer2} associated with this callback
         * @param item the MediaItem of this media item
         * @param status the result of DRM preparation.
         */
        public void onDrmPrepared(
                MediaPlayer2 mp, MediaItem item, @PrepareDrmStatusCode int status) { }
    }

    /**
     * Sets the callback to be invoked when the media source is ready for playback.
     *
     * @param eventCallback the callback that will be run
     * @param executor the executor through which the callback should be invoked
     */
    // This is a synchronous call.
    public abstract void setDrmEventCallback(@NonNull Executor executor,
                                             @NonNull DrmEventCallback eventCallback);

    /**
     * Clears the {@link DrmEventCallback}.
     */
    // This is a synchronous call.
    public abstract void clearDrmEventCallback();

    /**
     * The status codes for {@link DrmEventCallback#onDrmPrepared} listener.
     * <p>
     *
     * DRM preparation has succeeded.
     */
    public static final int PREPARE_DRM_STATUS_SUCCESS = 0;

    /**
     * The device required DRM provisioning but couldn't reach the provisioning server.
     */
    public static final int PREPARE_DRM_STATUS_PROVISIONING_NETWORK_ERROR = 1;

    /**
     * The device required DRM provisioning but the provisioning server denied the request.
     */
    public static final int PREPARE_DRM_STATUS_PROVISIONING_SERVER_ERROR = 2;

    /**
     * The DRM preparation has failed.
     */
    public static final int PREPARE_DRM_STATUS_PREPARATION_ERROR = 3;

    /**
     * The crypto scheme UUID that is not supported by the device.
     */
    public static final int PREPARE_DRM_STATUS_UNSUPPORTED_SCHEME = 4;

    /**
     * The hardware resources are not available, due to being in use.
     */
    public static final int PREPARE_DRM_STATUS_RESOURCE_BUSY = 5;

    @RestrictTo(LIBRARY)
    @IntDef(flag = false, /*prefix = "PREPARE_DRM_STATUS",*/ value = {
            PREPARE_DRM_STATUS_SUCCESS,
            PREPARE_DRM_STATUS_PROVISIONING_NETWORK_ERROR,
            PREPARE_DRM_STATUS_PROVISIONING_SERVER_ERROR,
            PREPARE_DRM_STATUS_PREPARATION_ERROR,
            PREPARE_DRM_STATUS_UNSUPPORTED_SCHEME,
            PREPARE_DRM_STATUS_RESOURCE_BUSY,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PrepareDrmStatusCode {}

    /**
     * Retrieves the DRM Info associated with the current source
     *
     * @throws IllegalStateException if called before being prepared
     */
    public abstract DrmInfo getDrmInfo();

    /**
     * Prepares the DRM for the current source
     * <p>
     * If {@link OnDrmConfigHelper} is registered, it will be called during
     * preparation to allow configuration of the DRM properties before opening the
     * DRM session. Note that the callback is called synchronously in the thread that called
     * {@link #prepareDrm}. It should be used only for a series of {@code getDrmPropertyString}
     * and {@code setDrmPropertyString} calls and refrain from any lengthy operation.
     * <p>
     * If the device has not been provisioned before, this call also provisions the device
     * which involves accessing the provisioning server and can take a variable time to
     * complete depending on the network connectivity.
     * prepareDrm() runs in non-blocking mode by launching the provisioning in the background and
     * returning. {@link DrmEventCallback#onDrmPrepared} will be called when provisioning and
     * preparation has finished. The application should check the status code returned with
     * {@link DrmEventCallback#onDrmPrepared} to proceed.
     * <p>
     *
     * @param uuid The UUID of the crypto scheme. If not known beforehand, it can be retrieved
     * from the source through {@link #getDrmInfo} or registering
     * {@link DrmEventCallback#onDrmInfo}.
     * @return a token which can be used to cancel the operation later with {@link #cancel}.
     */
    // This is an asynchronous call.
    public abstract Object prepareDrm(@NonNull UUID uuid);

    /**
     * Releases the DRM session
     * <p>
     * The player has to have an active DRM session and be in stopped, or prepared
     * state before this call is made.
     * A {@code reset()} call will release the DRM session implicitly.
     *
     * @throws NoDrmSchemeException if there is no active DRM session to release
     */
    // This is an asynchronous call.
    public abstract void releaseDrm() throws NoDrmSchemeException;

    /**
     * A key request/response exchange occurs between the app and a license server
     * to obtain or release keys used to decrypt encrypted content.
     * <p>
     * getDrmKeyRequest() is used to obtain an opaque key request byte array that is
     * delivered to the license server.  The opaque key request byte array is returned
     * in KeyRequest.data.  The recommended URL to deliver the key request to is
     * returned in KeyRequest.defaultUrl.
     * <p>
     * After the app has received the key request response from the server,
     * it should deliver to the response to the DRM engine plugin using the method
     * {@link #provideDrmKeyResponse}.
     *
     * @param keySetId is the key-set identifier of the offline keys being released when keyType is
     * {@link MediaDrm#KEY_TYPE_RELEASE}. It should be set to null for other key requests, when
     * keyType is {@link MediaDrm#KEY_TYPE_STREAMING} or {@link MediaDrm#KEY_TYPE_OFFLINE}.
     *
     * @param initData is the container-specific initialization data when the keyType is
     * {@link MediaDrm#KEY_TYPE_STREAMING} or {@link MediaDrm#KEY_TYPE_OFFLINE}. Its meaning is
     * interpreted based on the mime type provided in the mimeType parameter.  It could
     * contain, for example, the content ID, key ID or other data obtained from the content
     * metadata that is required in generating the key request.
     * When the keyType is {@link MediaDrm#KEY_TYPE_RELEASE}, it should be set to null.
     *
     * @param mimeType identifies the mime type of the content
     *
     * @param keyType specifies the type of the request. The request may be to acquire
     * keys for streaming, {@link MediaDrm#KEY_TYPE_STREAMING}, or for offline content
     * {@link MediaDrm#KEY_TYPE_OFFLINE}, or to release previously acquired
     * keys ({@link MediaDrm#KEY_TYPE_RELEASE}), which are identified by a keySetId.
     *
     * @param optionalParameters are included in the key request message to
     * allow a client application to provide additional message parameters to the server.
     * This may be {@code null} if no additional parameters are to be sent.
     *
     * @throws NoDrmSchemeException if there is no active DRM session
     */
    @NonNull
    public abstract MediaDrm.KeyRequest getDrmKeyRequest(
            @Nullable byte[] keySetId, @Nullable byte[] initData,
            @Nullable String mimeType, int keyType,
            @Nullable Map<String, String> optionalParameters)
            throws NoDrmSchemeException;

    /**
     * A key response is received from the license server by the app, then it is
     * provided to the DRM engine plugin using provideDrmKeyResponse. When the
     * response is for an offline key request, a key-set identifier is returned that
     * can be used to later restore the keys to a new session with the method
     * {@link #restoreDrmKeys}.
     * When the response is for a streaming or release request, null is returned.
     *
     * @param keySetId When the response is for a release request, keySetId identifies
     * the saved key associated with the release request (i.e., the same keySetId
     * passed to the earlier {@link #getDrmKeyRequest} call. It MUST be null when the
     * response is for either streaming or offline key requests.
     *
     * @param response the byte array response from the server
     *
     * @throws NoDrmSchemeException if there is no active DRM session
     * @throws DeniedByServerException if the response indicates that the
     * server rejected the request
     */
    // This is a synchronous call.
    public abstract byte[] provideDrmKeyResponse(
            @Nullable byte[] keySetId, @NonNull byte[] response)
            throws NoDrmSchemeException, DeniedByServerException;

    /**
     * Restore persisted offline keys into a new session.  keySetId identifies the
     * keys to load, obtained from a prior call to {@link #provideDrmKeyResponse}.
     *
     * @param keySetId identifies the saved key set to restore
     */
    // This is an asynchronous call.
    public abstract void restoreDrmKeys(@NonNull byte[] keySetId)
            throws NoDrmSchemeException;

    /**
     * Read a DRM engine plugin String property value, given the property name string.
     * <p>
     * @param propertyName the property name
     *
     * Standard fields names are:
     * {@link MediaDrm#PROPERTY_VENDOR}, {@link MediaDrm#PROPERTY_VERSION},
     * {@link MediaDrm#PROPERTY_DESCRIPTION}, {@link MediaDrm#PROPERTY_ALGORITHMS}
     */
    @NonNull
    public abstract String getDrmPropertyString(
            @NonNull String propertyName)
            throws NoDrmSchemeException;

    /**
     * Set a DRM engine plugin String property value.
     * <p>
     * @param propertyName the property name
     * @param value the property value
     *
     * Standard fields names are:
     * {@link MediaDrm#PROPERTY_VENDOR}, {@link MediaDrm#PROPERTY_VERSION},
     * {@link MediaDrm#PROPERTY_DESCRIPTION}, {@link MediaDrm#PROPERTY_ALGORITHMS}
     */
    // This is a synchronous call.
    public abstract void setDrmPropertyString(
            @NonNull String propertyName, @NonNull String value)
            throws NoDrmSchemeException;

    /**
     * Encapsulates the DRM properties of the source.
     */
    public abstract static class DrmInfo {
        /**
         * Returns the PSSH info of the media item for each supported DRM scheme.
         */
        public abstract Map<UUID, byte[]> getPssh();

        /**
         * Returns the intersection of the media item and the device DRM schemes.
         * It effectively identifies the subset of the source's DRM schemes which
         * are supported by the device too.
         */
        public abstract List<UUID> getSupportedSchemes();
    };  // DrmInfo

    /**
     * Thrown when a DRM method is called before preparing a DRM scheme through prepareDrm().
     * Extends MediaDrm.MediaDrmException
     */
    /* package */ static class NoDrmSchemeException extends Exception {
        /* package */ NoDrmSchemeException(String detailMessage) {
            super(detailMessage);
        }
    }

    /**
     * Definitions for the metrics that are reported via the {@link #getMetrics} call.
     */
    public static final class MetricsConstants {
        private MetricsConstants() {}

        /**
         * Key to extract the MIME type of the video track
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is a String.
         */
        public static final String MIME_TYPE_VIDEO = "android.media.mediaplayer.video.mime";

        /**
         * Key to extract the codec being used to decode the video track
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is a String.
         */
        public static final String CODEC_VIDEO = "android.media.mediaplayer.video.codec";

        /**
         * Key to extract the width (in pixels) of the video track
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is an integer.
         */
        public static final String WIDTH = "android.media.mediaplayer.width";

        /**
         * Key to extract the height (in pixels) of the video track
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is an integer.
         */
        public static final String HEIGHT = "android.media.mediaplayer.height";

        /**
         * Key to extract the count of video frames played
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is an integer.
         */
        public static final String FRAMES = "android.media.mediaplayer.frames";

        /**
         * Key to extract the count of video frames dropped
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is an integer.
         */
        public static final String FRAMES_DROPPED = "android.media.mediaplayer.dropped";

        /**
         * Key to extract the MIME type of the audio track
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is a String.
         */
        public static final String MIME_TYPE_AUDIO = "android.media.mediaplayer.audio.mime";

        /**
         * Key to extract the codec being used to decode the audio track
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is a String.
         */
        public static final String CODEC_AUDIO = "android.media.mediaplayer.audio.codec";

        /**
         * Key to extract the duration (in milliseconds) of the
         * media being played
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is a long.
         */
        public static final String DURATION = "android.media.mediaplayer.durationMs";

        /**
         * Key to extract the playing time (in milliseconds) of the
         * media being played
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is a long.
         */
        public static final String PLAYING = "android.media.mediaplayer.playingMs";

        /**
         * Key to extract the count of errors encountered while
         * playing the media
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is an integer.
         */
        public static final String ERRORS = "android.media.mediaplayer.err";

        /**
         * Key to extract an (optional) error code detected while
         * playing the media
         * from the {@link MediaPlayer2#getMetrics} return value.
         * The value is an integer.
         */
        public static final String ERROR_CODE = "android.media.mediaplayer.errcode";

    }
}
