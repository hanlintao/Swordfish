/*******************************************************************************
 * Copyright (c) 2007 - 2025 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/

class TermSearch {

    electron = require('electron');

    glossary: string = '';

    constructor() {
        this.electron.ipcRenderer.send('get-theme');
        this.electron.ipcRenderer.on('set-theme', (event: Electron.IpcRendererEvent, theme: string) => {
            (document.getElementById('theme') as HTMLLinkElement).href = theme;
        });
        document.addEventListener('keydown', (event: KeyboardEvent) => {
            if (event.code === 'Enter' || event.code === 'NumpadEnter') {
                this.search();
            }
            if (event.code === 'Escape') {
                this.electron.ipcRenderer.send('close-termSearch');
            }
        });
        this.electron.ipcRenderer.send('get-languages');
        this.electron.ipcRenderer.on('set-languages', (event: Electron.IpcRendererEvent, arg: any) => {
            this.setLanguages(arg);
        });
        (document.getElementById('searchButton') as HTMLButtonElement).addEventListener('click', () => {
            this.search()
        });
        this.electron.ipcRenderer.send('get-glossary-param');
        this.electron.ipcRenderer.on('set-glossary', (event: Electron.IpcRendererEvent, glossary: string) => {
            this.glossary = glossary;
        });
        this.electron.ipcRenderer.on('set-selected-text', (event: Electron.IpcRendererEvent, arg: { selected: string, lang?: string, srcLang: string, tgtLang: string }) => {
            this.setParams(arg);
        });
        this.electron.ipcRenderer.on('refresh-search-results', () => {
            console.log('收到刷新搜索结果请求');
            // 如果有搜索文本，重新执行搜索
            let searchText = (document.getElementById('searchText') as HTMLInputElement).value;
            if (searchText.trim()) {
                console.log('重新执行搜索:', searchText);
                this.search();
            }
        });
        (document.getElementById('similarity') as HTMLSelectElement).value = '70';
        (document.getElementById('searchText') as HTMLInputElement).focus();
        (document.getElementById('languagesSelect') as HTMLSelectElement).addEventListener('change', () => {
            let code: string = (document.getElementById('languagesSelect') as HTMLSelectElement).value;
            if (this.isBiDi(code)) {
                (document.getElementById('searchText') as HTMLInputElement).dir = 'rtl';
            }
        });
        setTimeout(() => {
            this.electron.ipcRenderer.send('set-height', { window: 'termSearch', width: document.body.clientWidth, height: document.body.clientHeight });
        }, 200);
    }

    setLanguages(arg: any): void {
        let array: LanguageInterface[] = arg.languages;
        let languageOptions: string = '<option value="none">Select Language</option>';
        for (let lang of array) {
            languageOptions = languageOptions + '<option value="' + lang.code + '">' + lang.description + '</option>';
        }
        let languageSelect: HTMLSelectElement = document.getElementById('languagesSelect') as HTMLSelectElement;
        languageSelect.innerHTML = languageOptions;
        languageSelect.value = arg.srcLang;
        this.electron.ipcRenderer.send('get-selection');
    }

    setParams(arg: { selected: string, lang?: string, srcLang: string, tgtLang: string }): void {
        (document.getElementById('searchText') as HTMLInputElement).value = arg.selected;
        if (arg.lang) {
            (document.getElementById('languagesSelect') as HTMLSelectElement).value = arg.lang;
            if (this.isBiDi(arg.lang)) {
                (document.getElementById('searchText') as HTMLInputElement).dir = 'rtl';
            }
        }
    }

    search(): void {
        let searchInput: HTMLInputElement = document.getElementById('searchText') as HTMLInputElement;
        let searchText: string = searchInput.value;
        if (searchText === '') {
            this.electron.ipcRenderer.send('show-message', { type: 'warning', message: 'Enter term to search', parent: 'termSearch' });
            return;
        }
        let languagesSelect: HTMLSelectElement = document.getElementById('languagesSelect') as HTMLSelectElement;
        let lang: string = languagesSelect.value;
        if (lang === 'none') {
            this.electron.ipcRenderer.send('show-message', { type: 'warning', message: 'Select language', parent: 'termSearch' });
            return;
        }
        let caseSensitive: HTMLInputElement = document.getElementById('caseSensitive') as HTMLInputElement;
        let similarity: string = (document.getElementById('similarity') as HTMLSelectElement).value;
        this.electron.ipcRenderer.send('search-terms', {
            searchStr: searchText,
            srcLang: lang,
            similarity: Number.parseInt(similarity, 10),
            caseSensitive: caseSensitive.checked,
            glossary: this.glossary
        });
    }

    isBiDi(code: string): boolean {
        return code.startsWith("ar") || code.startsWith("fa") || code.startsWith("az") || code.startsWith("ur")
            || code.startsWith("pa-PK") || code.startsWith("ps") || code.startsWith("prs") || code.startsWith("ug")
            || code.startsWith("he") || code.startsWith("ji") || code.startsWith("yi");
    }
}
