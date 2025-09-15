import { assertUnreachable } from './hacks';
import { getAppropriateFileSuffix } from "./model/syntaxHighlighting";
import { TextProbeStyle } from './model/TextProbeManager';
import WindowState from './model/WindowState';
import { TextSpanStyle } from "./ui/create/createTextSpanIndicator";
import UIElements from './ui/UIElements';

type Theme = 'auto' | 'light' | 'dark';

interface Settings {
  editorContents?: string;
  theme?: Theme;
  captureStdio?: boolean;
  captureTraces?: boolean;
  autoflushTraces?: boolean;
  duplicateProbeOnAttrClick?: boolean;
  showAllProperties?: boolean;
  positionRecoveryStrategy?: string;
  astCacheStrategy?: string;
  probeWindowStates?: WindowState[];
  syntaxHighlighting?: SyntaxHighlightingLanguageId;
  mainArgsOverride?: string[] | null;
  customFileSuffix?: string | null;
  locationStyle?: TextSpanStyle | null;
  textProbeStyle?: TextProbeStyle | null;
  hideSettingsPanel?: boolean;
  groupPropertiesByAspect?: boolean;
  autoShortenPropertyNames?: boolean;
  activeWorkspacePath?: string;
  shouldRerunWorkspaceTestsOnChange?: boolean;
}

let settingsObj: Settings | null = null;



const clearHashFromLocation = () => history.replaceState('', document.title, `${window.location.pathname}${window.location.search}`);

window.saveStateAsUrl = () => {
  const encoded = encodeURIComponent(JSON.stringify(settings.get()));
  // delete location.hash;'
  // console.log('loc:', location.toString());
  navigator.clipboard.writeText(
    `${window.location.origin}${window.location.pathname}${window.location.search}${window.location.search.length === 0 ? '?' : '&'}settings=${encoded}`
  );
  const btn = new UIElements().saveAsUrlButton;
  const saveText = btn.textContent;
  setTimeout(() => {
    btn.textContent = saveText;
    btn.style.border = 'unset';
    delete (btn.style as any).border;
  }, 1000);
  btn.textContent = `Copied to clipboard`;
  btn.style.border = '1px solid green'
}

const settings = {
  get: (): Settings => {
    if (!settingsObj) {
      let settingsMatch: RegExpExecArray | null;
      if ((settingsMatch = /[?&]settings=[^?&]+/.exec(location.search)) != null) {
        const trimmedSearch = settingsMatch.index === 0
          ? (
            settingsMatch[0].length < location.search.length
              ? `?${location.search.slice(settingsMatch[0].length + 1)}`
              : `${location.search.slice(0, settingsMatch.index)}${location.search.slice(settingsMatch.index + settingsMatch[0].length)}`
          )
          : `${location.search.slice(0, settingsMatch.index)}${location.search.slice(settingsMatch.index + settingsMatch[0].length)}`
        ;

        history.replaceState('', document.title, `${window.location.pathname}${trimmedSearch}`);
        try {
          settingsObj = JSON.parse(decodeURIComponent(settingsMatch[0].slice(`?settings=`.length)))
          clearHashFromLocation();
          if (settingsObj) {
            settings.set(settingsObj);
          }
        } catch (e) {
          console.warn('Invalid windowState in hash', e);
        }
      }
      if (!settingsObj) {
        try {
          // TODO remove 'pasta-settings' fallback after an appropriate amount of time
          settingsObj = JSON.parse(localStorage.getItem('codeprober-settings') || localStorage.getItem('pasta-settings') || '{}');
        } catch (e) {
          console.warn('Bad data in localStorage, resetting settings', e);
          settingsObj = {};
        }
      }
    }
    return settingsObj || {};
  },
  set: (newSettings: Settings) => {
    settingsObj = newSettings;
    localStorage.setItem('codeprober-settings', JSON.stringify(settingsObj));
  },

  getEditorContents: () => settings.get().editorContents,
  setEditorContents: (editorContents: string) => settings.set({ ...settings.get(), editorContents }),

  getTheme: () => settings.get().theme ?? 'auto',
  setTheme: (theme: Theme) => settings.set({ ...settings.get(), theme }),
  isLightTheme: (): boolean => {
    let ret = settings.get().theme;
    if (ret === undefined) {
      const oldValue = (settings.get() as any).lightTheme as boolean | undefined;
      if (oldValue !== undefined) {
        // The user set light/dark theme using the old checkbox, port settings value to new value
        ret = oldValue ? 'light' : 'dark';
      }
    }

    const theme = ret ?? 'auto';
    switch (theme) {
      case 'auto': {
        if (window.matchMedia) {
          return !window.matchMedia('(prefers-color-scheme: dark)').matches;
        }
        // Default to dark mode
        return false;
      }
      case 'dark': return false;
      case 'light': return true;
      default:
        assertUnreachable(theme);
        return false;
    }
  },
  // setTheme: (lightTheme: boolean) => settings.set({ ...settings.get(), lightTheme }),

  shouldDuplicateProbeOnAttrClick: () => settings.get().duplicateProbeOnAttrClick ?? true,
  setShouldDuplicateProbeOnAttrClick: (duplicateProbeOnAttrClick: boolean) => settings.set({ ...settings.get(), duplicateProbeOnAttrClick }),
  shouldCaptureStdio: () => settings.get().captureStdio ?? true,
  setShouldCaptureStdio: (captureStdio: boolean) => settings.set({ ...settings.get(), captureStdio }),
  shouldCaptureTraces: () => settings.get().captureTraces ?? false,
  setShouldCaptureTraces: (captureTraces: boolean) => settings.set({ ...settings.get(), captureTraces }),
  shouldAutoflushTraces: () => settings.get().autoflushTraces ?? true,
  setShouldAutoflushTraces: (autoflushTraces: boolean) => settings.set({ ...settings.get(), autoflushTraces }),

  getPositionRecoveryStrategy: () => settings.get().positionRecoveryStrategy ?? 'ALTERNATE_PARENT_CHILD',
  setPositionRecoveryStrategy: (positionRecoveryStrategy: string) => settings.set({ ...settings.get(), positionRecoveryStrategy }),

  getAstCacheStrategy: () => settings.get().astCacheStrategy ?? 'PARTIAL',
  setAstCacheStrategy: (astCacheStrategy: string) => settings.set({ ...settings.get(), astCacheStrategy }),

  getProbeWindowStates: (): WindowState[] => {
    const ret = settings.get().probeWindowStates ?? [];

    return ret.map((item) => {
      if (typeof item.data === 'undefined') {
        // Older variant of this data, upgrade it
        return {
          modalPos: item.modalPos,
          data: {
            type: 'probe',
            locator: (item as any).locator, // as any to access previously typed data
            property: (item as any).property, // as any to access previously typed data
            nested: {},
          }
        };
      }
      return item;
    });
  },
  setProbeWindowStates: (probeWindowStates: WindowState[]) => settings.set({ ...settings.get(), probeWindowStates }),

  getSyntaxHighlighting: () => settings.get().syntaxHighlighting ?? 'java',
  setSyntaxHighlighting: (syntaxHighlighting: SyntaxHighlightingLanguageId) => settings.set({ ...settings.get(), syntaxHighlighting }),

  getMainArgsOverride: () => settings.get().mainArgsOverride ?? null,
  setMainArgsOverride: (mainArgsOverride: string[] | null) => settings.set({ ...settings.get(), mainArgsOverride }),

  getCustomFileSuffix: () => settings.get().customFileSuffix ?? null,
  setCustomFileSuffix: (customFileSuffix: string | null) => settings.set({ ...settings.get(), customFileSuffix }),
  getCurrentFileSuffix: (): string => settings.getCustomFileSuffix() ?? `.${getAppropriateFileSuffix(settings.getSyntaxHighlighting())}`,

  shouldShowAllProperties: () => settings.get().showAllProperties ?? false,
  setShouldShowAllProperties: (showAllProperties: boolean) => settings.set({ ...settings.get(), showAllProperties }),

  getLocationStyle: () => settings.get().locationStyle ?? 'full',
  setLocationStyle: (locationStyle: TextSpanStyle | null) => settings.set({ ...settings.get(), locationStyle }),

  getTextProbeStyle: () => settings.get().textProbeStyle ?? 'angle-brackets',
  setTextProbeStyle: (textProbeStyle: TextProbeStyle | null) => settings.set({ ...settings.get(), textProbeStyle }),

  shouldHideSettingsPanel: () => settings.get()?.hideSettingsPanel ?? false,
  setShouldHideSettingsPanel: (shouldHide: boolean) => settings.set({ ...settings.get(), hideSettingsPanel: shouldHide }),

  shouldGroupPropertiesByAspect: () => settings.get()?.groupPropertiesByAspect ?? true,
  setShouldGroupPropertiesByAspect: (shouldHide: boolean) => settings.set({ ...settings.get(), groupPropertiesByAspect: shouldHide }),

  shouldAutoShortenPropertyNames: () => settings.get()?.autoShortenPropertyNames ?? true,
  setShouldAutoShortenPropertyNames: (autoShortenPropertyNames: boolean) => settings.set({ ...settings.get(), autoShortenPropertyNames }),

  getActiveWorkspacePath: () => settings.get()?.activeWorkspacePath ?? null,
  setActiveWorkspacePath: (activeWorkspacePath: string) => settings.set({ ...settings.get(), activeWorkspacePath }),
  shouldRerunWorkspaceTestsOnChange: () => settings.get().shouldRerunWorkspaceTestsOnChange ?? false,
  setShouldRerunWorkspaceTestsOnChange: (shouldRerunWorkspaceTestsOnChange: boolean) => settings.set({ ...settings.get(), shouldRerunWorkspaceTestsOnChange }),

  shouldEnableTesting: () => window.location.search.includes('enableTesting=true'),
};

export default settings;
