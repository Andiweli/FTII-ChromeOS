#pragma once

#include <stdbool.h>

#ifdef __ANDROID__
bool ft2AndroidSetupWorkspace(void);
void ft2AndroidSetFullscreenPreference(bool enabled);
void ft2AndroidPrepareForExit(void);
bool ft2AndroidNeedsAudioRollback(void);
bool ft2AndroidMarkAudioRolledBack(void);
/* Historical name: this now tests the exposed storage root. */
bool ft2AndroidAtWorkspaceRoot(void);
const char *ft2AndroidGetWorkspacePath(void);
#endif
