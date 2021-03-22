VERSION := 1.0.392
GROUP_ID := io.github.ganadist.sqlite4java
ARTIFACT_ID := libsqlite4java-osx-arm64
LOCAL_URL := file://$(HOME)/.m2/repository
POMFILE := libsqlite4java-osx-arm64.pom


CFLAGS := -O2 -fPIC
CFLAGS += -W -Wall -Wno-unused -Wno-parentheses #-Werror

CFLAGS += -DNDEBUG -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_ENABLE_FTS3 -DSQLITE_ENABLE_FTS3_PARENTHESIS -DSQLITE_ENABLE_MEMORY_MANAGEMENT -DSQLITE_ENABLE_STAT2 -DHAVE_READLINE=0 -DSQLITE_THREADSAFE=1 -DSQLITE_THREAD_OVERRIDE_LOCK=-1 -DTEMP_STORE=1  -DSQLITE_OMIT_DEPRECATED -DSQLITE_OS_UNIX=1 -DSQLITE_ENABLE_RTREE=1 -Isqlite

KERNEL := $(shell uname -s)
HARDWARE := $(shell uname -m)

ifeq ($(KERNEL),Linux)
	OS := linux
	SHARED_LIB_EXT := so
	CFLAGS += -fpic -DLINUX -D_LARGEFILE64_SOURCE -D_GNU_SOURCE -D_LITTLE_ENDIAN -fno-omit-frame-pointer -fno-strict-aliasing -static-libgcc
	LDFLAGS := -shared -fPIC
	JAVA_HOME := /usr/lib/jvm/java-8-openjdk
	JAVA_CFLAGS := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
else
	ifeq ($(KERNEL),Darwin)
		OS := osx
		SHARED_LIB_EXT := dylib
		SYSROOT := /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk
		CFLAGS += -O2 -DNDEBUG -fPIC -D_LARGEFILE64_SOURCE -D_GNU_SOURCE -fno-omit-frame-pointer -fno-strict-aliasing
		CFLAGS += -DSQLITE_ENABLE_LOCKING_STYLE=0 -mmacosx-version-min=10.6 -DMAC_OS_X_VERSION_MIN_REQUIRED=1060
		LDFLAGS := -dynamiclib
		# XCode 12.2 does not provide JavaVM framework
		#LDFLAGS += -isysroot $(SYSROOT) -framework JavaVM
		JAVA_HOME := /Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home
		JAVA_HOME := /Library/Java/JavaVirtualMachines/zulu.jdk-8u282b08
		JAVA_CFLAGS := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/darwin
	else
		error
	endif

endif

ifeq ($(HARDWARE),x86_64)
	ARCH := amd64
else
	ifeq ($(HARDWARE),arm64)
		ARCH := arm64
	endif
endif

PLATFORM := $(OS)-$(ARCH)
OUT_DIR := out/$(PLATFORM)

libsqlite4java-$(PLATFORM)-$(VERSION).$(SHARED_LIB_EXT): $(OUT_DIR)/sqlite3.o $(OUT_DIR)/sqlite_wrap.o $(OUT_DIR)/sqlite3_wrap_manual.o $(OUT_DIR)/intarray.o
	$(CC) -o $@ $^ $(LDFLAGS)

swig/sqlite_wrap.c: swig/sqlite.i
	swig-2 -java -package com.almworks.sqlite4java -o $@ $<

$(OUT_DIR)/sqlite3.o: sqlite/sqlite3.c
	mkdir -p $(OUT_DIR)
	$(CC) -c -o $@ $< $(CFLAGS)

$(OUT_DIR)/sqlite_wrap.o: swig/sqlite_wrap.c
	mkdir -p $(OUT_DIR)
	$(CC) -c -o $@ $< $(CFLAGS) $(JAVA_CFLAGS)

$(OUT_DIR)/sqlite3_wrap_manual.o: native/sqlite3_wrap_manual.c
	mkdir -p $(OUT_DIR)
	$(CC) -c -o $@ $< $(CFLAGS) $(JAVA_CFLAGS)

$(OUT_DIR)/intarray.o: native/intarray.c
	mkdir -p $(OUT_DIR)
	$(CC) -c -o $@ $< $(CFLAGS)

clean:
	rm -rf $(OUT_DIR)

install: libsqlite4java-osx-arm64-$(VERSION).dylib
	mvn deploy:deploy-file \
		-Dsonar.skip=true \
		-DgeneratePom=false \
		-DgroupId=$(GROUP_ID) \
		-DartifactId=$(ARTIFACT_ID) \
		-Dversion=$(VERSION) \
		-Dpackaging=$(SHARED_LIB_EXT) \
		-Durl=$(LOCAL_URL) \
		-Dfile=$@ \
		-DpomFile=$(POMFILE)

