document.addEventListener('DOMContentLoaded', () => {
    const dateBadge = document.getElementById('dateBadge');
    const datePillsContainer = document.getElementById('datePillsContainer');
    const learnMoreBtn = document.getElementById('learnMoreBtn');
    const infoPanel = document.getElementById('infoPanel');
    const learnMoreIcon = document.getElementById('learnMoreIcon');

    const loadingState = document.getElementById('loadingState');
    const loadingText = document.getElementById('loadingText');
    const errorBanner = document.getElementById('errorBanner');
    const errorMessage = document.getElementById('errorMessage');
    const metaBar = document.getElementById('metaBar');
    const reportDateBadge = document.getElementById('reportDateBadge');
    const cveCountBadge = document.getElementById('cveCountBadge');
    const briefContent = document.getElementById('briefContent');

    // Configure marked options if loaded
    if (typeof marked !== 'undefined') {
        marked.setOptions({
            gfm: true,
            breaks: true
        });
    }

    // Calculate exact 14-day UTC date boundary (Today down to Today - 13 days)
    const now = new Date();
    const todayUTC = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));

    const minDateUTC = new Date(todayUTC);
    minDateUTC.setUTCDate(minDateUTC.getUTCDate() - 13); // 14 days total inclusive

    // Update range badge
    const badgeOptions = { month: 'short', day: 'numeric', timeZone: 'UTC' };
    const minFormatted = minDateUTC.toLocaleDateString('en-US', badgeOptions);
    const todayFormatted = todayUTC.toLocaleDateString('en-US', badgeOptions);
    dateBadge.textContent = `${minFormatted} – ${todayFormatted}`;

    // Build 14 quick-select pill models
    const validDates = [];
    for (let i = 0; i < 14; i++) {
        const d = new Date(todayUTC);
        d.setUTCDate(d.getUTCDate() - i);
        const isoStr = d.toISOString().split('T')[0];

        let title = '';
        if (i === 0) title = 'Today';
        else if (i === 1) title = 'Yesterday';
        else title = d.toLocaleDateString('en-US', { weekday: 'short', timeZone: 'UTC' });

        const dateSub = d.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            timeZone: 'UTC'
        });
        validDates.push({ dateStr: isoStr, title: title, sub: dateSub });
    }

    let activeDateStr = validDates[1].dateStr; // Default to yesterday

    function renderPills() {
        datePillsContainer.replaceChildren();
        validDates.forEach(item => {
            const pill = document.createElement('button');
            pill.type = 'button';
            pill.className = `date-pill ${item.dateStr === activeDateStr ? 'active' : ''}`;
            
            const titleSpan = document.createElement('span');
            titleSpan.textContent = item.title;

            const subSpan = document.createElement('span');
            subSpan.className = 'pill-sub';
            subSpan.textContent = item.sub;

            pill.appendChild(titleSpan);
            pill.appendChild(subSpan);

            pill.addEventListener('click', () => {
                if (activeDateStr === item.dateStr) return;
                activeDateStr = item.dateStr;
                updateActivePills();
                loadBrief(activeDateStr);
            });

            datePillsContainer.appendChild(pill);
        });
    }

    function updateActivePills() {
        const pills = datePillsContainer.querySelectorAll('.date-pill');
        pills.forEach((pill, idx) => {
            if (validDates[idx].dateStr === activeDateStr) {
                pill.classList.add('active');
                pill.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
            } else {
                pill.classList.remove('active');
            }
        });
    }

    function showLoading(selectedDate) {
        loadingText.textContent = `Fetching threat brief for ${selectedDate}...`;
        loadingState.hidden = false;
        errorBanner.hidden = true;
        metaBar.hidden = true;
        briefContent.replaceChildren();
    }

    function showError(message) {
        errorMessage.textContent = `${message}. Ensure the backend service is running.`;
        errorBanner.hidden = false;
        loadingState.hidden = true;
        metaBar.hidden = true;
        briefContent.replaceChildren();
    }

    function renderMarkdown(container, markdownText) {
        container.replaceChildren();
        if (typeof marked !== 'undefined' && marked.parse) {
            const rawHtml = marked.parse(markdownText);
            const doc = new DOMParser().parseFromString(rawHtml, 'text/html');
            const dangerousElements = doc.querySelectorAll('script, iframe, object, embed, form');
            dangerousElements.forEach(el => el.remove());
            const allElements = doc.querySelectorAll('*');
            allElements.forEach(el => {
                for (const attr of Array.from(el.attributes)) {
                    const isInlineHandler = attr.name.startsWith('on');
                    const isJsProtocol = attr.value.trim().toLowerCase().startsWith('javascript:');
                    if (isInlineHandler || isJsProtocol) {
                        el.removeAttribute(attr.name);
                    }
                }
            });
            container.append(...doc.body.childNodes);
        } else {
            container.textContent = markdownText;
        }
    }

    function showBriefing(data, selectedDate) {
        loadingState.hidden = true;
        errorBanner.hidden = true;

        if (data && data.content) {
            const totalCves = typeof data.totalCount === 'number' ? data.totalCount : null;
            reportDateBadge.textContent = `Report Date: ${data.date || selectedDate}`;
            
            if (totalCves !== null) {
                cveCountBadge.textContent = `${totalCves} Total CVEs Processed`;
                cveCountBadge.hidden = false;
            } else {
                cveCountBadge.hidden = true;
            }
            metaBar.hidden = false;
            renderMarkdown(briefContent, data.content);
        } else if (typeof data === 'string') {
            metaBar.hidden = true;
            renderMarkdown(briefContent, data);
        } else {
            metaBar.hidden = true;
            briefContent.replaceChildren();
            briefContent.textContent = `No briefing content available for ${selectedDate}.`;
        }
    }

    async function loadBrief(selectedDate) {
        if (!selectedDate) return;

        showLoading(selectedDate);

        try {
            const apiUrl = `/brief?date=${encodeURIComponent(selectedDate)}`;
            const response = await fetch(apiUrl);
            
            if (!response.ok) {
                const errorText = await response.text().catch(() => '');
                throw new Error(`HTTP ${response.status}: ${response.statusText || errorText}`);
            }

            const data = await response.json();
            showBriefing(data, selectedDate);
        } catch (err) {
            showError(err.message);
        }
    }

    // Learn More Panel Toggle
    if (learnMoreBtn && infoPanel) {
        learnMoreBtn.addEventListener('click', () => {
            const isVisible = infoPanel.classList.toggle('visible');
            if (learnMoreIcon) {
                learnMoreIcon.textContent = isVisible ? '\u25B4' : '\u25BE';
            }
        });
    }

    // Initialize UI
    renderPills();
    loadBrief(activeDateStr);

    // Chat functionality
    const chatToggleBtn = document.getElementById('chatToggleBtn');
    const chatPanel = document.getElementById('chatPanel');
    const chatCloseBtn = document.getElementById('chatCloseBtn');
    const chatMessages = document.getElementById('chatMessages');
    const chatInput = document.getElementById('chatInput');
    const chatSendBtn = document.getElementById('chatSendBtn');

    // Conversation history (client-side only)
    let conversation = [];

    function openChat() {
        chatPanel.classList.add('open');
        document.body.classList.add('chat-open');
        if (chatToggleBtn) {
            chatToggleBtn.style.display = 'none';
        }
    }

    function closeChat() {
        chatPanel.classList.remove('open');
        document.body.classList.remove('chat-open');
        if (chatToggleBtn) {
            chatToggleBtn.style.display = 'flex';
        }
    }

    if (chatToggleBtn) {
        chatToggleBtn.addEventListener('click', openChat);
    }

    if (chatCloseBtn) {
        chatCloseBtn.addEventListener('click', closeChat);
    }

    function addMessageToChat(role, content) {
        const msgDiv = document.createElement('div');
        msgDiv.className = `chat-message ${role}`;
        if (role === 'assistant') {
            renderMarkdown(msgDiv, content);
        } else {
            msgDiv.textContent = content;
        }
        chatMessages.appendChild(msgDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function setChatInputEnabled(enabled) {
        chatInput.disabled = !enabled;
        chatSendBtn.disabled = !enabled;
    }

    function showTypingIndicator() {
        const typingDiv = document.createElement('div');
        typingDiv.className = 'chat-message assistant typing-indicator';
        typingDiv.id = 'typingIndicator';
        for (let i = 0; i < 3; i++) {
            typingDiv.appendChild(document.createElement('span'));
        }
        chatMessages.appendChild(typingDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function removeTypingIndicator() {
        const typingDiv = document.getElementById('typingIndicator');
        if (typingDiv) {
            typingDiv.remove();
        }
    }

    async function sendMessage() {
        const content = chatInput.value.trim();
        if (!content) return;

        addMessageToChat('user', content);
        conversation.push({ role: 'user', content: content });
        chatInput.value = '';
        setChatInputEnabled(false);
        showTypingIndicator();

        try {
            const response = await fetch('/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ messages: conversation })
            });

            if (!response.ok) {
                const errorText = await response.text().catch(() => '');
                throw new Error(`HTTP ${response.status}: ${errorText}`);
            }

            const data = await response.json();
            const assistantMessage = data.message;

            removeTypingIndicator();
            addMessageToChat('assistant', assistantMessage.content);
            conversation.push(assistantMessage);

            if (conversation.length > 10) {
                conversation = conversation.slice(-10);
            }
        } catch (err) {
            removeTypingIndicator();
            addMessageToChat('assistant', 'Sorry, something went wrong: ' + err.message);
        } finally {
            setChatInputEnabled(true);
            chatInput.focus();
        }
    }

    if (chatSendBtn) {
        chatSendBtn.addEventListener('click', sendMessage);
    }

    if (chatInput) {
        chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }

    // Show default welcome message
    const welcomeLines = [
        "Hey! I'm your ThreatBrief Assistant. I can help you with:",
        "",
        "- **Vulnerability counts** for any date in the past 14 days",
        "- **Top vulnerabilities** ranked by severity",
        "- **Recent dates** with available threat briefs",
        "",
        "Ask me anything about the threat landscape!"
    ];
    addMessageToChat('assistant', welcomeLines.join('\n'));

    // Open chat by default on large screens
    if (window.innerWidth >= 1024) {
        openChat();
    }
});
