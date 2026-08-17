// for finding memory leaks in debug mode with Visual Studio
#if defined _DEBUG && defined _MSC_VER
#include <crtdbg.h>
#endif

// for detecting if musl or glibc is used
#if defined(__linux__)
  /* Only Linux has glibc's <features.h>. On BSDs (including FreeBSD) and others,
     skip this block to avoid a missing-header error. */
  #ifdef __has_include
    #if __has_include(<features.h>)
      #include <features.h>
    #endif
  #else
    /* If the compiler doesn't support __has_include, assume features.h exists on glibc. */
    #include <features.h>
  #endif
  /* If <features.h> didn't define glibc's GNU extensions, assume musl. */
  #ifndef __USE_GNU
    #define __MUSL__
  #endif
#endif

#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#elif !defined __ANDROID__
#include <iconv.h>
#endif
#include "ft2_unicode.h"

#ifdef _WIN32

// Windows routines
char *cp850ToUtf8(char *src)
{
	int32_t retVal;

	if (src == NULL)
		return NULL;

	int32_t srcLen = (int32_t)strlen(src);
	if (srcLen <= 0)
		return NULL;

	int32_t reqSize = MultiByteToWideChar(850, 0, src, srcLen, 0, 0);
	if (reqSize <= 0)
		return NULL;

	wchar_t *w = (wchar_t *)malloc((reqSize + 1) * sizeof (wchar_t));
	if (w == NULL)
		return NULL;

	w[reqSize] = 0;

	retVal = MultiByteToWideChar(850, 0, src, srcLen, w, reqSize);
	if (!retVal)
	{
		free(w);
		return NULL;
	}

	srcLen = (int32_t)wcslen(w);
	if (srcLen <= 0)
		return NULL;

	reqSize = WideCharToMultiByte(CP_UTF8, 0, w, srcLen, 0, 0, 0, 0);
	if (reqSize <= 0)
	{
		free(w);
		return NULL;
	}

	char *x = (char *)malloc((reqSize + 1) * sizeof (char));
	if (x == NULL)
	{
		free(w);
		return NULL;
	}

	x[reqSize] = '\0';

	retVal = WideCharToMultiByte(CP_UTF8, 0, w, srcLen, x, reqSize, 0, 0);
	free(w);

	if (!retVal)
	{
		free(x);
		return NULL;
	}

	return x;
}

UNICHAR *cp850ToUnichar(char *src)
{
	if (src == NULL)
		return NULL;

	int32_t srcLen = (int32_t)strlen(src);
	if (srcLen <= 0)
		return NULL;

	int32_t reqSize = MultiByteToWideChar(850, 0, src, srcLen, 0, 0);
	if (reqSize <= 0)
		return NULL;

	UNICHAR *w = (wchar_t *)malloc((reqSize + 1) * sizeof (wchar_t));
	if (w == NULL)
		return NULL;

	w[reqSize] = 0;

	int32_t retVal = MultiByteToWideChar(850, 0, src, srcLen, w, reqSize);
	if (!retVal)
	{
		free(w);
		return NULL;
	}

	return w;
}

char *utf8ToCp850(char *src, bool removeIllegalChars)
{
	if (src == NULL)
		return NULL;

	int32_t srcLen = (int32_t)strlen(src);
	if (srcLen <= 0)
		return NULL;

	int32_t reqSize = MultiByteToWideChar(CP_UTF8, 0, src, srcLen, 0, 0);
	if (reqSize <= 0)
		return NULL;

	wchar_t *w = (wchar_t *)malloc((reqSize + 1) * sizeof (wchar_t));
	if (w == NULL)
		return NULL;

	w[reqSize] = 0;

	int32_t retVal = MultiByteToWideChar(CP_UTF8, 0, src, srcLen, w, reqSize);
	if (!retVal)
	{
		free(w);
		return NULL;
	}

	srcLen = (int32_t)wcslen(w);
	if (srcLen <= 0)
	{
		free(w);
		return NULL;
	}

	reqSize = WideCharToMultiByte(850, 0, w, srcLen, 0, 0, 0, 0);
	if (reqSize <= 0)
	{
		free(w);
		return NULL;
	}

	char *x = (char *)malloc((reqSize + 1) * sizeof (char));
	if (x == NULL)
	{
		free(w);
		return NULL;
	}

	x[reqSize] = '\0';

	retVal = WideCharToMultiByte(850, 0, w, srcLen, x, reqSize, 0, 0);
	free(w);

	if (!retVal)
	{
		free(x);
		return NULL;
	}

	if (removeIllegalChars)
	{
		// remove illegal characters (only allow certain nordic ones)
		for (int32_t i = 0; i < reqSize; i++)
		{
			const int8_t ch = (const int8_t)x[i];
			if (ch != '\0' && ch < 32 &&
			    ch != -124 && ch != -108 && ch != -122 && ch != -114 && ch != -103 &&
			    ch != -113 && ch != -101 && ch != -99 && ch != -111 && ch != -110)
			{
				x[i] = ' '; // character not allowed, turn it into space
			}
		}
	}

	return x;
}

char *unicharToCp850(UNICHAR *src, bool removeIllegalChars)
{
	if (src == NULL)
		return NULL;

	int32_t srcLen = (int32_t)UNICHAR_STRLEN(src);
	if (srcLen <= 0)
		return NULL;

	int32_t reqSize = WideCharToMultiByte(850, 0, src, srcLen, 0, 0, 0, 0);
	if (reqSize <= 0)
		return NULL;

	char *x = (char *)malloc((reqSize + 1) * sizeof (char));
	if (x == NULL)
		return NULL;

	x[reqSize] = '\0';

	int32_t retVal = WideCharToMultiByte(850, 0, src, srcLen, x, reqSize, 0, 0);
	if (!retVal)
	{
		free(x);
		return NULL;
	}

	if (removeIllegalChars)
	{
		// remove illegal characters (only allow certain nordic ones)
		for (int32_t i = 0; i < reqSize; i++)
		{
			const int8_t ch = (const int8_t)x[i];
			if (ch != '\0' && ch < 32 &&
			    ch != -124 && ch != -108 && ch != -122 && ch != -114 && ch != -103 &&
			    ch != -113 && ch != -101 && ch != -99 && ch != -111 && ch != -110)
			{
				x[i] = ' '; // character not allowed, turn it into space
			}
		}
	}

	return x;
}

#elif defined __ANDROID__

/* Android's C library does not provide iconv. Keep FT2's on-disk UTF-8 paths
** and its internal CP850 text model by using a compact, deterministic codec.
*/
static const uint16_t cp850HighToUnicode[128] =
{
	0x00C7, 0x00FC, 0x00E9, 0x00E2, 0x00E4, 0x00E0, 0x00E5, 0x00E7,
	0x00EA, 0x00EB, 0x00E8, 0x00EF, 0x00EE, 0x00EC, 0x00C4, 0x00C5,
	0x00C9, 0x00E6, 0x00C6, 0x00F4, 0x00F6, 0x00F2, 0x00FB, 0x00F9,
	0x00FF, 0x00D6, 0x00DC, 0x00F8, 0x00A3, 0x00D8, 0x00D7, 0x0192,
	0x00E1, 0x00ED, 0x00F3, 0x00FA, 0x00F1, 0x00D1, 0x00AA, 0x00BA,
	0x00BF, 0x00AE, 0x00AC, 0x00BD, 0x00BC, 0x00A1, 0x00AB, 0x00BB,
	0x2591, 0x2592, 0x2593, 0x2502, 0x2524, 0x00C1, 0x00C2, 0x00C0,
	0x00A9, 0x2563, 0x2551, 0x2557, 0x255D, 0x00A2, 0x00A5, 0x2510,
	0x2514, 0x2534, 0x252C, 0x251C, 0x2500, 0x253C, 0x00E3, 0x00C3,
	0x255A, 0x2554, 0x2569, 0x2566, 0x2560, 0x2550, 0x256C, 0x00A4,
	0x00F0, 0x00D0, 0x00CA, 0x00CB, 0x00C8, 0x0131, 0x00CD, 0x00CE,
	0x00CF, 0x2518, 0x250C, 0x2588, 0x2584, 0x00A6, 0x00CC, 0x2580,
	0x00D3, 0x00DF, 0x00D4, 0x00D2, 0x00F5, 0x00D5, 0x00B5, 0x00FE,
	0x00DE, 0x00DA, 0x00DB, 0x00D9, 0x00FD, 0x00DD, 0x00AF, 0x00B4,
	0x00AD, 0x00B1, 0x2017, 0x00BE, 0x00B6, 0x00A7, 0x00F7, 0x00B8,
	0x00B0, 0x00A8, 0x00B7, 0x00B9, 0x00B3, 0x00B2, 0x25A0, 0x00A0
};

static size_t writeUtf8(uint32_t codepoint, char *destination)
{
	if (codepoint <= 0x7F)
	{
		destination[0] = (char)codepoint;
		return 1;
	}

	if (codepoint <= 0x7FF)
	{
		destination[0] = (char)(0xC0 | (codepoint >> 6));
		destination[1] = (char)(0x80 | (codepoint & 0x3F));
		return 2;
	}

	destination[0] = (char)(0xE0 | (codepoint >> 12));
	destination[1] = (char)(0x80 | ((codepoint >> 6) & 0x3F));
	destination[2] = (char)(0x80 | (codepoint & 0x3F));
	return 3;
}

static uint32_t readUtf8(const uint8_t **source, const uint8_t *end)
{
	const uint8_t *text = *source;
	if (text >= end)
		return 0xFFFD;

	const uint8_t first = *text++;
	if (first < 0x80)
	{
		*source = text;
		return first;
	}

	uint32_t codepoint;
	int32_t continuationCount;
	if ((first & 0xE0) == 0xC0)
	{
		codepoint = first & 0x1F;
		continuationCount = 1;
	}
	else if ((first & 0xF0) == 0xE0)
	{
		codepoint = first & 0x0F;
		continuationCount = 2;
	}
	else if ((first & 0xF8) == 0xF0)
	{
		codepoint = first & 0x07;
		continuationCount = 3;
	}
	else
	{
		*source = text;
		return 0xFFFD;
	}

	for (int32_t index = 0; index < continuationCount; index++)
	{
		if (text >= end || (*text & 0xC0) != 0x80)
		{
			*source = text;
			return 0xFFFD;
		}
		codepoint = (codepoint << 6) | (*text++ & 0x3F);
	}

	*source = text;
	return codepoint;
}

static uint8_t unicodeToCp850(uint32_t codepoint)
{
	if (codepoint < 128)
		return (uint8_t)codepoint;

	for (int32_t index = 0; index < 128; index++)
	{
		if (cp850HighToUnicode[index] == codepoint)
			return (uint8_t)(index + 128);
	}

	return '?';
}

char *cp850ToUtf8(char *src)
{
	if (src == NULL || src[0] == '\0')
		return NULL;

	const size_t sourceLength = strlen(src);
	char *output = (char *)malloc((sourceLength * 3) + 1);
	if (output == NULL)
		return NULL;

	size_t outputOffset = 0;
	for (size_t index = 0; index < sourceLength; index++)
	{
		const uint8_t byte = (uint8_t)src[index];
		const uint32_t codepoint = byte < 128 ? byte : cp850HighToUnicode[byte - 128];
		outputOffset += writeUtf8(codepoint, output + outputOffset);
	}

	output[outputOffset] = '\0';
	return output;
}

char *utf8ToCp850(char *src, bool removeIllegalChars)
{
	if (src == NULL || src[0] == '\0')
		return NULL;

	const size_t sourceLength = strlen(src);
	char *output = (char *)malloc(sourceLength + 1);
	if (output == NULL)
		return NULL;

	const uint8_t *position = (const uint8_t *)src;
	const uint8_t *end = position + sourceLength;
	size_t outputOffset = 0;
	while (position < end)
	{
		uint8_t converted = unicodeToCp850(readUtf8(&position, end));
		if (removeIllegalChars && converted != '\0' && converted < 32)
			converted = ' ';

		output[outputOffset++] = (char)converted;
	}

	output[outputOffset] = '\0';
	return output;
}

#else

// non-Windows routines
char *cp850ToUtf8(char *src)
{
	if (src == NULL)
		return NULL;

	size_t srcLen = strlen(src);
	if (srcLen <= 0)
		return NULL;

	iconv_t cd = iconv_open("UTF-8", "850");
	if (cd == (iconv_t)-1)
		return NULL;

	size_t outLen = srcLen * 4; // should be sufficient

	char *outBuf = (char *)calloc(outLen + 1, sizeof (char));
	if (outBuf == NULL)
		return NULL;

	char *inPtr = src;
	size_t inLen = srcLen;
	char *outPtr = outBuf;

#if defined(__NetBSD__) || defined(__sun) || defined(sun)
	int32_t rc = iconv(cd, (const char **)&inPtr, &inLen, &outPtr, &outLen);
#else
	int32_t rc = iconv(cd, &inPtr, &inLen, &outPtr, &outLen);
#endif
	iconv(cd, NULL, NULL, &outPtr, &outLen); // flush
	iconv_close(cd);

	if (rc == -1)
	{
		free(outBuf);
		return NULL;
	}

	outBuf[outLen] = '\0';

	return outBuf;
}

char *utf8ToCp850(char *src, bool removeIllegalChars)
{
	if (src == NULL)
		return NULL;

	size_t srcLen = strlen(src);
	if (srcLen <= 0)
		return NULL;

#ifdef __APPLE__
	iconv_t cd = iconv_open("850//TRANSLIT//IGNORE", "UTF-8-MAC");
#elif defined(__NetBSD__) || defined(__sun) || defined(sun)
	iconv_t cd = iconv_open("850", "UTF-8");
#elif defined(__MUSL__)
	iconv_t cd = iconv_open("cp850", "UTF-8");
#else
	iconv_t cd = iconv_open("850//TRANSLIT//IGNORE", "UTF-8");
#endif
	if (cd == (iconv_t)-1)
		return NULL;

	size_t outLen = srcLen * 4; // should be sufficient

	char *outBuf = (char *)calloc(outLen + 1, sizeof (char));
	if (outBuf == NULL)
		return NULL;

	char *inPtr = src;
	size_t inLen = srcLen;
	char *outPtr = outBuf;

#if defined(__NetBSD__) || defined(__sun) || defined(sun)
	int32_t rc = iconv(cd, (const char **)&inPtr, &inLen, &outPtr, &outLen);
#else
	int32_t rc = iconv(cd, &inPtr, &inLen, &outPtr, &outLen);
#endif
	iconv(cd, NULL, NULL, &outPtr, &outLen); // flush
	iconv_close(cd);

	if (rc == -1)
	{
		free(outBuf);
		return NULL;
	}

	outBuf[outLen] = '\0';

	if (removeIllegalChars)
	{
		// remove illegal characters (only allow certain nordic ones)
		for (size_t i = 0; i < outLen; i++)
		{
			const int8_t ch = (const int8_t)outBuf[i];
			if (ch != '\0' && ch < 32 &&
			    ch != -124 && ch != -108 && ch != -122 && ch != -114 && ch != -103 &&
			    ch != -113 && ch != -101 && ch != -99 && ch != -111 && ch != -110)
			{
				outBuf[i] = ' '; // character not allowed, turn it into space
			}
		}
	}

	return outBuf;
}
#endif
