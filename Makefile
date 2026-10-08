VERSIONS    := android/gradle/libs.versions.toml
version      = $(shell sed -n 's/^$(1) *= *"\(.*\)"/\1/p' $(VERSIONS))
NDK_VERSION := $(call version,ndk)
MIN_SDK     := $(call version,minSdk)
ZIG_VERSION := $(shell sed -n 's/.*minimum_zig_version = "\(.*\)".*/\1/p' terminal-native/build.zig.zon)

export ANDROID_HOME ?= $(HOME)/Android/Sdk
export ANDROID_NDK_HOME := $(ANDROID_HOME)/ndk/$(NDK_VERSION)
SDKMANAGER := $(ANDROID_HOME)/cmdline-tools/latest/bin/sdkmanager

GRADLE   := android/gradlew -p android
ABIS     := arm64-v8a armeabi-v7a x86_64 x86
JNI_LIBS := android/app/src/main/jniLibs
GO_AAR   := android/data/libs/kubenexus.aar
ZIG_SO   := $(ABIS:%=$(JNI_LIBS)/%/libghostty_jni.so)
TOOLS    := build/tools

fingerprint = $(shell { files=$$(find $(1) -type f ! -name '*_test.go' ! -path '*/testdata/*' | LC_ALL=C sort); \
	echo "$$files"; cat $$files; echo $(2); } | git hash-object --stdin)
GO_STAMP  := build/native/go/$(call fingerprint,k8s-engine/go.mod k8s-engine/go.sum k8s-engine/pkg k8s-engine/scripts/rename_jni.sh Makefile,$(NDK_VERSION) $(MIN_SDK) $(shell go version 2>/dev/null))
ZIG_STAMP := build/native/zig/$(call fingerprint,terminal-native/src terminal-native/build.zig terminal-native/build.zig.zon Makefile,$(NDK_VERSION) $(MIN_SDK) $(shell zig version 2>/dev/null))

LICENSES_DIR ?= legal/compliance
LICENSES_OUT := $(abspath $(LICENSES_DIR))
LICENSES_TXT := $(LICENSES_OUT)/kubenexus-licenses.txt

.DEFAULT_GOAL := help
.PHONY: help versions ndk jni verify-jni debug release bundle install test lint fmt \
        licenses licenses-clean go-notices generate-kube-openapi-spec clean

help: ## List the targets
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  \033[36m%-28s\033[0m %s\n", $$1, $$2}'

versions: ## Print the pinned NDK, minSdk, Zig version and the ABIs
	@echo ndk=$(NDK_VERSION)
	@echo min_sdk=$(MIN_SDK)
	@echo zig=$(ZIG_VERSION)
	@echo abis=$(ABIS)

ndk: $(ANDROID_NDK_HOME)/source.properties ## Install the pinned NDK if it is missing

$(ANDROID_NDK_HOME)/source.properties:
	yes | $(SDKMANAGER) --install "ndk;$(NDK_VERSION)" >/dev/null

jni: $(GO_AAR) $(ZIG_SO) ## Build the native libraries that are missing or out of date

$(GO_STAMP) $(ZIG_STAMP):
	@rm -rf $(@D) && mkdir -p $(@D) && touch $@

$(GO_AAR): $(GO_STAMP) | ndk
	cd k8s-engine && go build -o $(CURDIR)/$(TOOLS)/ golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind
	cd k8s-engine && PATH="$(CURDIR)/$(TOOLS):$$PATH" gomobile bind -target android -androidapi $(MIN_SDK) \
		-trimpath -ldflags=-extldflags=-Wl,-z,max-page-size=16384 -o $(CURDIR)/$@ ./pkg/client
	k8s-engine/scripts/rename_jni.sh $@ kubenexus_client

$(ZIG_SO) &: $(ZIG_STAMP) | ndk
	cd terminal-native && zig build -Doptimize=ReleaseSmall -Dapi=$(MIN_SDK) \
		-Dsysroot="$$(echo $(ANDROID_NDK_HOME)/toolchains/llvm/prebuilt/*/sysroot)" --prefix $(CURDIR)/$(JNI_LIBS)
	touch $(ZIG_SO)

verify-jni: jni ## Check every ABI has both libraries, 16 KB aligned and free of afl++
	@readelf=$$(echo $(ANDROID_NDK_HOME)/toolchains/llvm/prebuilt/*/bin/llvm-readelf); \
	tmp=$$(mktemp -d); trap 'rm -rf "$$tmp"' EXIT; \
	unzip -q $(GO_AAR) 'jni/*' -d "$$tmp"; \
	fail=0; \
	for abi in $(ABIS); do \
	  for so in $(JNI_LIBS)/$$abi/libghostty_jni.so "$$tmp/jni/$$abi/libkubenexus_client.so"; do \
	    name=$${so#$$tmp/}; \
	    if [ ! -f "$$so" ]; then echo "missing: $$name"; fail=1; continue; fi; \
	    for align in $$("$$readelf" -lW "$$so" | awk '$$1 == "LOAD" { print $$NF }'); do \
	      if [ $$(($$align)) -lt 16384 ]; then echo "aligned below 16 KB: $$name"; fail=1; break; fi; \
	    done; \
	    if strings "$$so" | grep -q 'afl_'; then echo "contains afl++ (GPL-2.0): $$name"; fail=1; fi; \
	  done; \
	done; \
	[ $$fail = 0 ] && echo "OK: $(ABIS)"

debug: jni ## Build the debug APK
	$(GRADLE) assembleDebug

release: jni ## Build the release APK
	$(GRADLE) assembleRelease

bundle: jni ## Build the release App Bundle for Play
	$(GRADLE) bundleRelease

install: jni ## Install the debug APK on a connected device
	$(GRADLE) installDebug

test: jni ## Run the Go and Android unit tests
	cd k8s-engine && go test ./...
	$(GRADLE) test

lint: ## Run go vet and Android lint
	cd k8s-engine && go vet ./...
	$(GRADLE) lint

fmt: ## Format Go and Zig sources and apply Android lint fixes
	cd k8s-engine && go fmt ./...
	cd terminal-native && zig fmt build.zig src
	$(GRADLE) lintFix

licenses: ## Write the licence compliance report to LICENSES_DIR (default legal/compliance)
	$(GRADLE) :app:exportComplianceLibrariesRelease -PaboutLibraries.exportPath=$(LICENSES_OUT)
	@mv $(LICENSES_OUT)/export.txt $(LICENSES_TXT)
	@mv $(LICENSES_OUT)/export.csv $(LICENSES_OUT)/kubenexus-licenses.csv
	@libs=$$(sed -n '/^LIBRARIES:/,/^LICENSES:/p' $(LICENSES_TXT) | grep ';'); \
	echo; echo "Libraries: $$(echo "$$libs" | wc -l)"; \
	echo; echo "Licences in use:"; echo "$$libs" | cut -d';' -f3 | tr ',' '\n' | sed 's/^ *//' | sort -u | sed 's/^/  /'; \
	echo; echo "Native components:"; echo "$$libs" | grep 'dev.hridaya.kubenexus' | cut -d';' -f1,3 | sed 's/^/  /'
	@for section in "ARTIFACTS WITHOUT LICENSE" "UNKNOWN LICENSES"; do \
	  entries=$$(awk -v want="$$section:" '$$0 == want {on=1; next} on && /^[A-Z][A-Z /-]*:$$/ {exit} on && NF' $(LICENSES_TXT)); \
	  echo; if [ -z "$$entries" ]; then echo "$$section: none"; else echo "$$section, review these:"; echo "$$entries" | sed 's/^/  /'; fi; \
	done
	@echo; echo "Report: $(LICENSES_TXT)"

licenses-clean: ## Remove the licence compliance report
	rm -rf $(LICENSES_DIR)

go-notices: ## Regenerate the in-app licences for the Go engine
	python3 k8s-engine/scripts/generate_go_notices.py

generate-kube-openapi-spec: ## Re-record the k8s-engine test payloads from a live cluster
	k8s-engine/scripts/record-testdata.sh

clean: ## Remove native libraries, build outputs and caches
	rm -rf build $(JNI_LIBS) $(GO_AAR) android/data/libs/kubenexus-sources.jar
	rm -rf terminal-native/.zig-cache terminal-native/zig-out
	$(GRADLE) clean
