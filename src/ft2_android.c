#include "ft2_android.h"

#ifdef __ANDROID__

#include <SDL2/SDL.h>
#include <SDL2/SDL_system.h>
#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static char workspacePath[PATH_MAX + 1];
static char storageRootPath[PATH_MAX + 1];

#define FT2_ANDROID_COMMAND_SET_FULLSCREEN_PREFERENCE 0x8001
#define FT2_ANDROID_COMMAND_PREPARE_EXIT 0x8002
#define FT2_ANDROID_LOW_LATENCY_MARKER "ft2-low-latency-audio-v1"
#define FT2_ANDROID_AUDIO_ROLLBACK_MARKER "ft2-audio-rollback-v1"

static bool directoryIsAccessible(const char *path)
{
	struct stat status;
	return stat(path, &status) == 0 && S_ISDIR(status.st_mode) &&
		access(path, R_OK | W_OK | X_OK) == 0;
}

bool ft2AndroidSetupWorkspace(void)
{
	const char *internalStorage = SDL_AndroidGetInternalStoragePath();
	if (internalStorage == NULL || internalStorage[0] == '\0')
		return false;

	const char *workspaceParent = internalStorage;
	const char *workspaceName = "workspace";

	/* With ChromeOS' explicit "all files" permission, /storage/emulated/0 is
	** the root shown as Play files. Keep FT II's own folder there, while still
	** allowing Disk Op. to navigate to sibling folders such as Download.
	*/
	const char *sharedStorage = "/storage/emulated/0";
	if (directoryIsAccessible(sharedStorage))
	{
		workspaceParent = sharedStorage;
		workspaceName = "FT II";
	}

	int32_t length = snprintf(storageRootPath, sizeof (storageRootPath), "%s", workspaceParent);
	if (length <= 0 || (size_t)length >= sizeof (storageRootPath))
		return false;

	length = snprintf(workspacePath, sizeof (workspacePath), "%s/%s", workspaceParent, workspaceName);
	if (length <= 0 || (size_t)length >= sizeof (workspacePath))
		return false;

	if (mkdir(workspacePath, S_IRWXU) != 0 && errno != EEXIST)
		return false;

	struct stat status;
	if (stat(workspacePath, &status) != 0 || !S_ISDIR(status.st_mode))
		return false;

	if (setenv("HOME", workspacePath, 1) != 0)
		return false;

	return chdir(workspacePath) == 0;
}

void ft2AndroidSetFullscreenPreference(bool enabled)
{
	/* Delivered on Android's UI thread through Ft2Activity.onUnhandledMessage().
	** The small marker is readable by the launcher process before SDL starts.
	*/
	SDL_AndroidSendMessage(FT2_ANDROID_COMMAND_SET_FULLSCREEN_PREFERENCE, enabled ? 1 : 0);
}

void ft2AndroidPrepareForExit(void)
{
	/* Queue an opaque Android View before SDL destroys its separate SurfaceView.
	** Ft2Activity keeps it visible for one compositor frame during task removal.
	*/
	SDL_AndroidSendMessage(FT2_ANDROID_COMMAND_PREPARE_EXIT, 1);
}

static bool getInternalMarkerPath(const char *markerName, char *path, size_t pathSize)
{
	const char *internalStorage = SDL_AndroidGetInternalStoragePath();
	if (internalStorage == NULL || internalStorage[0] == '\0')
		return false;

	const int32_t length = snprintf(path, pathSize, "%s/%s", internalStorage,
		markerName);
	return length > 0 && (size_t)length < pathSize;
}

bool ft2AndroidNeedsAudioRollback(void)
{
	char lowLatencyMarkerPath[PATH_MAX + 1];
	char rollbackMarkerPath[PATH_MAX + 1];
	return getInternalMarkerPath(FT2_ANDROID_LOW_LATENCY_MARKER,
			lowLatencyMarkerPath, sizeof (lowLatencyMarkerPath)) &&
		getInternalMarkerPath(FT2_ANDROID_AUDIO_ROLLBACK_MARKER,
			rollbackMarkerPath, sizeof (rollbackMarkerPath)) &&
		access(lowLatencyMarkerPath, F_OK) == 0 &&
		access(rollbackMarkerPath, F_OK) != 0;
}

bool ft2AndroidMarkAudioRolledBack(void)
{
	char markerPath[PATH_MAX + 1];
	if (!getInternalMarkerPath(FT2_ANDROID_AUDIO_ROLLBACK_MARKER,
		markerPath, sizeof (markerPath)))
		return false;

	FILE *file = fopen(markerPath, "wb");
	if (file == NULL)
		return false;

	const bool writeSucceeded = fputs("FT II audio defaults restored\n", file) >= 0;
	const bool closeSucceeded = fclose(file) == 0;
	return writeSucceeded && closeSucceeded;
}

bool ft2AndroidAtWorkspaceRoot(void)
{
	if (storageRootPath[0] == '\0')
		return false;

	char currentPath[PATH_MAX + 1];
	if (getcwd(currentPath, sizeof (currentPath)) == NULL)
		return false;

	return strcmp(currentPath, storageRootPath) == 0;
}

const char *ft2AndroidGetWorkspacePath(void)
{
	return workspacePath[0] != '\0' ? workspacePath : NULL;
}

#endif
