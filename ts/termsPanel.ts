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

class TermsPanel {

    electron = require('electron');

    container: HTMLDivElement;
    projectId: string;
    glossaryId: string = '';
    selected: Term | undefined = undefined;
    selectedIndex: number = 0;
    terms: Term[] = [];
    rows: HTMLTableRowElement[] = [];
    table: HTMLTableElement;

    originSpan: HTMLSpanElement;

    constructor(div: HTMLDivElement, projectId: string) {
        this.container = div;
        this.projectId = projectId;

        let tableHolder: HTMLDivElement = document.createElement('div');
        tableHolder.classList.add('divContainer');
        this.container.appendChild(tableHolder);

        this.table = document.createElement('table');
        this.table.classList.add('stripes');
        this.table.classList.add('zoomable');
        tableHolder.appendChild(this.table);

        let toolbar: HTMLDivElement = document.createElement('div');
        toolbar.classList.add('toolbar');
        toolbar.classList.add('middle');
        toolbar.classList.add('roundedBottom');
        this.container.appendChild(toolbar);

        let getTerms = document.createElement('a');
        getTerms.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 0 24 24" width="24"><path d="M17.01 14h-.8l-.27-.27c.98-1.14 1.57-2.61 1.57-4.23 0-3.59-2.91-6.5-6.5-6.5s-6.5 3-6.5 6.5H2l3.84 4 4.16-4H6.51C6.51 7 8.53 5 11.01 5s4.5 2.01 4.5 4.5c0 2.48-2.02 4.5-4.5 4.5-.65 0-1.26-.14-1.82-.38L7.71 15.1c.97.57 2.09.9 3.3.9 1.61 0 3.08-.59 4.22-1.57l.27.27v.79l5.01 4.99L22 19l-4.99-5z"/></svg>' +
            '<span class="tooltiptext topTooltip">Get Glossary Terms</span>';
        getTerms.className = 'tooltip topTooltip ';
        getTerms.addEventListener('click', () => {
            this.electron.ipcRenderer.send('request-apply-terminology');
        });
        toolbar.appendChild(getTerms);

        let getAllTerms = document.createElement('a');
        getAllTerms.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 0 24 24" width="24"><path d="M3 17.25V21h3.75l11.06-11.06-3.75-3.75L3 17.25zM20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z"/></svg>' +
            '<span class="tooltiptext topTooltip">显示术语库全部术语</span>';
        getAllTerms.className = 'tooltip topTooltip ';
        getAllTerms.addEventListener('click', () => {
            if (this.glossaryId) {
                console.log('[termsPanel] 请求术语库ID:', this.glossaryId);
                this.electron.ipcRenderer.send('get-glossary-terms', { glossary: this.glossaryId });
            } else {
                console.log('[termsPanel] 未设置术语库ID');
            }
        });
        toolbar.appendChild(getAllTerms);

        this.originSpan = document.createElement('span');
        this.originSpan.style.marginLeft = '10px';
        this.originSpan.style.marginTop = '4px';
        toolbar.appendChild(this.originSpan);

        let config: MutationObserverInit = { attributes: true, childList: false, subtree: false };
        let observer = new MutationObserver((mutationsList) => {
            for (let mutation of mutationsList) {
                if (mutation.type === 'attributes') {
                    tableHolder.style.height = (this.container.clientHeight - toolbar.clientHeight) + 'px';
                }
            }
        });
        observer.observe(this.container, config);

        this.electron.ipcRenderer.on('set-glossary-terms', (event: any, terms: any[]) => {
            console.log('[termsPanel] 收到全部术语:', terms.length);
            this.terms = [];
            for (let term of terms) {
                this.terms.push({
                    srcLang: 'zh',
                    tgtLang: 'en',
                    source: term.source || '',
                    target: term.target || '',
                    origin: ''
                });
            }
            this.refreshTable();
        });

        this.electron.ipcRenderer.on('set-matched-terms', (event: any, matchedTerms: any[]) => {
            console.log('[termsPanel] 收到匹配术语:', matchedTerms.length);
            this.terms = [];
            for (let term of matchedTerms) {
                this.terms.push({
                    srcLang: 'zh',
                    tgtLang: 'en',
                    source: term.source || '',
                    target: term.target || '',
                    origin: '匹配'
                });
            }
            this.refreshTable();
        });
    }

    clear(): void {
        this.table.innerHTML = '<tr/>';
        this.selected = undefined;
        this.terms = [];
        this.originSpan.innerText = '';
        this.rows = [];
    }

    showLoading(): void {
        this.table.innerHTML = '';
        let row: HTMLTableRowElement = document.createElement('tr');
        let td: HTMLTableCellElement = document.createElement('td');
        td.colSpan = 3;
        td.innerText = '正在匹配术语...';
        td.style.textAlign = 'center';
        td.style.fontStyle = 'italic';
        row.appendChild(td);
        this.table.appendChild(row);
        this.terms = [];
        this.rows = [];
    }

    getTerm(i: number) {
        return this.terms[i - 1].target;
    }

    getSelected(): string {
        if (this.selected) {
            return this.selected.target;
        }
        return '';
    }

    setTerms(terms: Term[]): void {
        this.terms = terms;
        this.table.innerHTML = '';
        this.rows = [];
        if (terms.length === 0) {
            let row: HTMLTableRowElement = document.createElement('tr');
            let td: HTMLTableCellElement = document.createElement('td');
            td.colSpan = 3;
            td.innerText = '未匹配到术语';
            td.style.textAlign = 'center';
            row.appendChild(td);
            this.table.appendChild(row);
            return;
        }
        let length: number = terms.length;
        for (let i: number = 0; i < length; i++) {
            let term: Term = terms[i];
            let row: HTMLTableRowElement = document.createElement('tr');
            row.addEventListener('click', () => {
                this.selected = term;
                let collection = this.table.getElementsByClassName('selected');
                if (collection.length > 0) {
                    collection[0].classList.remove('selected');
                }
                this.originSpan.innerText = term.origin;
                row.classList.add('selected');
                this.selectedIndex = i;
                this.insertTermTranslation(term.target);
            });
            this.table.appendChild(row);

            let td: HTMLTableCellElement = document.createElement('td');
            td.classList.add('center');
            td.classList.add('middle');
            td.classList.add('initial');
            td.innerText = '' + (i + 1);
            row.appendChild(td);

            let source: HTMLTableCellElement = document.createElement('td');
            source.innerText = term.source;
            source.style.width = '49%';
            source.classList.add('initial')
            if (TranslationView.isBiDi(term.srcLang)) {
                source.dir = 'rtl';
            }
            row.appendChild(source);

            let target: HTMLTableCellElement = document.createElement('td');
            target.innerText = term.target;
            target.style.width = '49%';
            if (TranslationView.isBiDi(term.tgtLang)) {
                target.dir = 'rtl';
            }
            row.appendChild(target);

            this.rows.push(row);
        }
        if (this.rows.length > 0) {
            this.rows[0].classList.add('selected');
            this.selected = this.terms[0];
            this.originSpan.innerText = this.terms[0].origin;
            this.selectedIndex = 0;
        }
    }

    selectNextTerm(): void {
        if (this.selectedIndex < this.rows.length - 1) {
            this.rows[this.selectedIndex].classList.remove('selected');
            this.selectedIndex++;
            this.rows[this.selectedIndex].classList.add('selected');
            this.selected = this.terms[this.selectedIndex];
            this.originSpan.innerText = this.terms[this.selectedIndex].origin;
            this.rows[this.selectedIndex].scrollIntoView()
        }
    }

    selectPreviousTerm(): void {
        if (this.selectedIndex > 0) {
            this.rows[this.selectedIndex].classList.remove('selected');
            this.selectedIndex--;
            this.rows[this.selectedIndex].classList.add('selected');
            this.selected = this.terms[this.selectedIndex];
            this.originSpan.innerText = this.terms[this.selectedIndex].origin;
            this.rows[this.selectedIndex].scrollIntoView()
        }
    }

    setGlossaryId(glossaryId: string): void {
        this.glossaryId = glossaryId;
        console.log('[termsPanel] 设置术语库ID:', glossaryId);
    }

    setMatchedTerms(matchedTerms: any[]): void {
        console.log('[termsPanel] 设置匹配术语:', matchedTerms.length);
        this.terms = [];
        for (let term of matchedTerms) {
            this.terms.push({
                srcLang: 'zh',
                tgtLang: 'en',
                source: term.source || '',
                target: term.target || '',
                origin: '匹配'
            });
        }
        this.setTerms(this.terms);
    }

    refreshTable(): void {
        this.setTerms(this.terms);
    }

    insertTermTranslation(translation: string): void {
        // 向翻译界面发送插入译文的事件
        this.electron.ipcRenderer.send('insert-translation', { translation: translation });
    }
}