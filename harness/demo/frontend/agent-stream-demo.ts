import type { AgentStreamHandlers, AgentPendingConfirmation } from '../frontend/agent-api-types';

export function runDemoStream(handlers: AgentStreamHandlers) {
  const fakeEventSource = createFakeStream([
    { event: 'status', data: { message: '初始化 agent...' } },
    { event: 'thinking', data: { content: '先检查当前项目。' } },
    { event: 'tool.call', data: { callId: '1', name: 'list_projects', title: '列出项目', riskLevel: 'READ', requiresConfirm: false } },
    { event: 'tool.running', data: { callId: '1' } },
    { event: 'tool.result', data: { callId: '1', ok: true, summary: '共 2 个项目', durationMs: 97 } },
    { event: 'delta', data: { content: '下面是我找到的项目。' } },
    { event: 'done', data: {} },
  ]);

  fakeEventSource.onmessage = ({ data }: MessageEvent) => {
    const parsed = JSON.parse(data);
    switch (parsed.event) {
      case 'delta':
        handlers.onDelta(parsed.data.content);
        break;
      case 'thinking':
        handlers.onThinking?.(parsed.data.content);
        break;
      case 'status':
        handlers.onStatus?.(parsed.data.message);
        break;
      case 'tool.result':
        handlers.onEvent?.(parsed.event, parsed.data);
        break;
      case 'done':
        handlers.onEvent?.(parsed.event, parsed.data);
        break;
    }
  };
}

function createFakeStream(items: Array<{ event: string; data: Record<string, unknown> }>) {
  let index = 0;
  const source: Partial<EventSource> = {
    onmessage: null,
    readyState: 0,
    close() {
      this.readyState = 2;
    },
  };

  queueMicrotask(() => {
    if (!source.onmessage) return;
    for (const item of items) {
      source.onmessage({ data: JSON.stringify(item) } as MessageEvent);
    }
    source.close();
  });

  return source as EventSource;
}
