export interface AgentStreamHandlers {
  onDelta: (content: string) => void;
  onThinking?: (content: string) => void;
  onStatus?: (status: string) => void;
  onData?: (data: Record<string, unknown>) => void;
  onEvent?: (event: string, data: Record<string, unknown>) => void;
  onSuggestions?: (items: string[]) => void;
}

function agentStreamApiBase(): string {
  if (API_BASE) return API_BASE;
  if (typeof window !== 'undefined' && process.env.NODE_ENV === 'development') {
    return `http://${window.location.hostname}:9999`;
  }
  return API_BASE;
}

export async function streamAgentChat(
  body: AgentStreamBody,
  apikey: string | null,
  handlers: AgentStreamHandlers,
  signal?: AbortSignal
): Promise<void> {
  const path = '/agent/v1/chat/stream';
  const response = await fetch(`${agentStreamApiBase()}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(apikey ? { apikey } : {}),
    },
    body: JSON.stringify(body),
    signal,
  });

  if (!response.ok) {
    const raw = await response.text().catch(() => response.statusText);
    // Never redirect from the Agent stream: the Agent page keeps the conversation and the pending
    // confirmation card alive so the user can sign in again and resume, instead of losing context.
    // The confirmation itself is persisted server-side and restorable after re-login.
    throw toApiError(response.status, raw);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw { status: 500, message: 'Streaming is not supported.' } satisfies ApiError;
  }

  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split(/\n\n/);
    buffer = blocks.pop() ?? '';

    for (const block of blocks) {
      const lines = block.split('\n');
      const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() ?? 'message';
      const dataLine = lines.find((line) => line.startsWith('data:'));
      if (!dataLine) continue;
      const payload = dataLine.slice(5).trim();
      if (!payload || payload === '[DONE]') continue;
      const parsed = JSON.parse(payload) as Record<string, unknown>;

      if (event === 'delta' && typeof parsed.content === 'string') {
        handlers.onDelta(parsed.content);
      } else if (event === 'message.delta' && typeof parsed.content === 'string') {
        handlers.onDelta(parsed.content);
      } else if (event === 'thinking' && typeof parsed.content === 'string') {
        handlers.onThinking?.(parsed.content);
      } else if (event === 'thinking.delta' && typeof parsed.content === 'string') {
        handlers.onThinking?.(parsed.content);
      } else if (event === 'status' && typeof parsed.message === 'string') {
        handlers.onStatus?.(parsed.message);
      } else if (event === 'data') {
        handlers.onData?.(parsed);
      } else if (event === 'suggestions' && Array.isArray(parsed.items)) {
        handlers.onSuggestions?.(parsed.items.filter((item): item is string => typeof item === 'string'));
      } else if (
        event === 'confirm.required' ||
        event === 'tool.call' ||
        event === 'tool.running' ||
        event === 'tool.result' ||
        event === 'tool.error' ||
        event === 'message.replace'
      ) {
        handlers.onEvent?.(event, parsed);
      } else if (event === 'error' && typeof parsed.message === 'string') {
        throw { status: 500, message: parsed.message } satisfies ApiError;
      }
    }
  }
}

export async function fetchAgentToolConfirmationStatuses(
  ids: string[],
  apikey: string | null
): Promise<Record<string, string>> {
  if (!ids.length) return {};
  const params = new URLSearchParams();
  ids.forEach((id) => params.append('ids', id));
  const result = await apiFetch<{ statuses?: Record<string, string> }>(
    `/agent/v1/tool-confirmations?${params.toString()}`,
    { apikey: apikey ?? undefined }
  );
  return result.statuses || {};
}

export async function cancelAgentToolConfirmation(id: string, apikey: string | null): Promise<void> {
  await apiFetch(`/agent/v1/tool-confirmations/${encodeURIComponent(id)}/cancel`, {
    method: 'POST',
    apikey: apikey ?? undefined,
  });
}

export interface AgentPendingConfirmation {
  callId: string;
  action: string;
  projectRef?: string | null;
  createdAt?: string;
  riskLevel?: string;
  arguments?: Record<string, unknown>;
  component?: AgentInteractiveComponent;
  requestMessage?: string;
  history?: Array<{ role?: string; content?: string }>;
}

export async function fetchPendingAgentConfirmations(
  apikey: string | null
): Promise<AgentPendingConfirmation[]> {
  const result = await apiFetch<{ items?: AgentPendingConfirmation[] }>('/agent/v1/tool-confirmations/pending', {
    apikey: apikey ?? undefined,
  });
  return Array.isArray(result.items) ? result.items : [];
}

export async function generateAgentSuggestions(body: { context?: string }, apikey: string | null): Promise<string[]> {
  const result = await apiFetch<{ items?: string[] }>('/agent/v1/suggestions', {
    method: 'POST',
    body,
    apikey: apikey ?? undefined,
  });
  return Array.isArray(result.items) ? result.items.filter((item): item is string => typeof item === 'string') : [];
}

export interface AgentFeedbackBody {
  taskId: string;
  messageId: number;
  rating: number;
  issueType?: string;
  comment?: string;
  allowImprovement?: boolean;
  userMessage?: string;
  agentReply?: string;
}

export async function submitAgentFeedback(body: AgentFeedbackBody, apikey: string | null): Promise<void> {
  await apiFetch('/agent/v1/feedback', {
    method: 'POST',
    body,
    apikey: apikey ?? undefined,
  });
}

/** One persisted Agent conversation, as stored server-side. */
export interface AgentSessionPayload {
  id: string;
  title: string;
