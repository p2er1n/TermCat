const state = {
  data: null,
  filtered: [],
  selected: null
};

const elements = {
  commitList: document.getElementById("commit-list"),
  commitDetail: document.getElementById("commit-detail"),
  filterInput: document.getElementById("filter-input"),
  toggleLogs: document.getElementById("toggle-logs"),
  commitCount: document.getElementById("meta-commit-count"),
  logCount: document.getElementById("meta-log-count"),
  generatedAt: document.getElementById("meta-generated-at")
};

function formatDate(isoString) {
  if (!isoString) {
    return "";
  }
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) {
    return isoString;
  }
  return date.toLocaleString();
}

function updateMeta() {
  const commitCount = state.data?.commitCount ?? 0;
  const logCount = state.data?.commits.filter((commit) => commit.files.length > 0).length ?? 0;
  elements.commitCount.textContent = String(commitCount);
  elements.logCount.textContent = String(logCount);
  elements.generatedAt.textContent = formatDate(state.data?.generatedAt);
}

function matchesFilter(commit, term) {
  if (!term) {
    return true;
  }
  const text = term.toLowerCase();
  if (commit.hash.toLowerCase().includes(text)) {
    return true;
  }
  if (commit.subject.toLowerCase().includes(text)) {
    return true;
  }
  return commit.files.some((file) => file.path.toLowerCase().includes(text));
}

function renderList() {
  elements.commitList.innerHTML = "";

  state.filtered.forEach((commit) => {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "commit-card";
    card.dataset.hash = commit.hash;
    if (state.selected?.hash === commit.hash) {
      card.classList.add("is-active");
    }

    const dateLabel = formatDate(commit.date);
    const fileCount = commit.files.length;

    card.innerHTML = `
      <div class="commit-card__hash">${commit.shortHash}</div>
      <div class="commit-card__subject">${commit.subject}</div>
      <div class="commit-card__meta">
        <span>${dateLabel}</span>
        <span>${fileCount} 条对话</span>
      </div>
    `;

    card.addEventListener("click", () => {
      state.selected = commit;
      renderList();
      renderDetail();
    });

    elements.commitList.appendChild(card);
  });
}

function renderDetail() {
  const commit = state.selected;
  if (!commit) {
    elements.commitDetail.innerHTML = `
      <div class="commit-detail__empty">
        <h2>请选择一个提交</h2>
        <p>左侧列表支持搜索和筛选。</p>
      </div>
    `;
    return;
  }

  const header = `
    <div class="detail-header">
      <div>
        <span class="detail-header__badge">${commit.shortHash}</span>
      </div>
      <h2>${commit.subject}</h2>
      <div class="detail-header__meta">
        <span>提交时间：${formatDate(commit.date)}</span>
        <span>对话数量：${commit.files.length}</span>
      </div>
    </div>
  `;

  if (commit.files.length === 0) {
    elements.commitDetail.innerHTML = `
      ${header}
      <div class="empty-state">这个提交没有修改 .waylog/history，对话记录为空。</div>
    `;
    return;
  }

  const logs = commit.files
    .map((file) => {
      const fileName = file.path.split("/").pop();
      const messages = parseMessages(file.content);
      const chatHtml = renderChat(messages);
      return `
        <details class="log-entry" open>
          <summary>
            ${fileName}
            <span class="log-entry__path">${file.path}</span>
            <span class="log-entry__count">${messages.length} 条消息</span>
          </summary>
          ${chatHtml}
        </details>
      `;
    })
    .join("");

  elements.commitDetail.innerHTML = `
    ${header}
    <div class="log-list">${logs}</div>
  `;
}

function escapeHtml(value) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function normalizeRole(name) {
  const key = name.toLowerCase();
  if (key.includes("user") || key.includes("用户") || key.includes("human")) {
    return "user";
  }
  if (key.includes("system") || key.includes("系统")) {
    return "system";
  }
  if (
    key.includes("assistant") ||
    key.includes("codex") ||
    key.includes("openai") ||
    key.includes("ai")
  ) {
    return "ai";
  }
  return "ai";
}

function parseMessages(content) {
  const lines = content.split(/\r?\n/);
  const messages = [];
  let current = null;
  let buffer = [];

  const pushMessage = () => {
    if (!current) {
      buffer = [];
      return;
    }
    const text = buffer.join("\n").trim();
    if (text) {
      messages.push({
        role: normalizeRole(current),
        name: current,
        text
      });
    }
    buffer = [];
  };

  lines.forEach((line) => {
    const match = line.match(/^\*\*(.+?)\*\*$/);
    if (match) {
      pushMessage();
      current = match[1].trim();
      return;
    }
    if (!current) {
      return;
    }
    buffer.push(line);
  });

  pushMessage();
  return messages;
}

function toHtml(text) {
  return escapeHtml(text).replace(/\n/g, "<br />");
}

function renderChat(messages) {
  if (!messages.length) {
    return `<div class="chat-thread"><div class="chat-empty">未解析到对话内容。</div></div>`;
  }
  const body = messages
    .map((message) => {
      if (message.role === "system") {
        return `
          <div class="message message--system">
            <div class="message__system">${toHtml(message.text)}</div>
          </div>
        `;
      }
      return `
        <div class="message message--${message.role}">
          <div class="message__meta">${message.name}</div>
          <div class="message__bubble">${toHtml(message.text)}</div>
        </div>
      `;
    })
    .join("");

  return `<div class="chat-thread">${body}</div>`;
}

function applyFilter() {
  const term = elements.filterInput.value.trim();
  const onlyLogs = elements.toggleLogs.checked;

  state.filtered = state.data.commits.filter((commit) => {
    if (onlyLogs && commit.files.length === 0) {
      return false;
    }
    return matchesFilter(commit, term);
  });

  if (state.filtered.length > 0) {
    if (!state.selected || !state.filtered.some((commit) => commit.hash === state.selected.hash)) {
      state.selected = state.filtered[0];
    }
  } else {
    state.selected = null;
  }

  renderList();
  renderDetail();
}

function init() {
  fetch("data.json")
    .then((response) => {
      if (!response.ok) {
        throw new Error("无法读取 data.json，请先运行 generate-history.ps1 生成数据。");
      }
      return response.json();
    })
    .then((data) => {
      state.data = data;
      state.filtered = data.commits;
      state.selected = data.commits[0] || null;
      updateMeta();
      renderList();
      renderDetail();
    })
    .catch((error) => {
      elements.commitDetail.innerHTML = `
        <div class="empty-state">
          <strong>数据加载失败：</strong> ${error.message}
        </div>
      `;
    });

  elements.filterInput.addEventListener("input", applyFilter);
  elements.toggleLogs.addEventListener("change", applyFilter);
}

init();
