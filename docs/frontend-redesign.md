# Frontend redesign review

## Initial audit

The existing React Query, role-based routing, shared UI components and calendar already cover the required workflows. The redesign preserves them. The main presentation issues were very small supporting text at mobile breakpoints, scattered bright colors/gradients, marketing-style dashboard titles competing with operational information, dense generator controls, minimally styled AI input, and questions/unsupported constraints blended into ordinary notices. Existing dialogs, native form validation, focus handling, loading/error/empty states and the mobile calendar list were retained.

## Design direction

A restrained slate workspace with teal primary actions, neutral surfaces, modest borders, clear type hierarchy and consistent radii. Semantic draft/published/error colors remain distinct. Component states include explicit keyboard focus, disabled controls, hover feedback and the existing reduced-motion setting. No external font, dependency, API change or backend change.

The presentation layer is isolated in frontend/src/redesign.css and loaded after existing styles from main.tsx. Keeping compatibility styles separate limits unrelated churn; later consolidation can remove redundant original declarations after visual approval.

## Screen improvements

- Dashboard: task-oriented headings, clearer labels explaining actual counts, restrained statistic cards and workspace actions. Counts still derive from existing API responses; no fabricated trends.
- Application/navigation: dark slate navigation, quieter active indicator, larger touch targets, improved account contrast, and explicit mobile navigation aria-controls. Existing skip link and keyboard navigation retained.
- Employees: direct directory heading, improved table rhythm, readable row actions, clearer employee-number styling, consistent forms, focus states, dialogs and empty states.
- Availability: clear weekly heading, better row spacing, readable times/status and consistent touch targets. One recurring interval and existing confirmation behavior unchanged.
- Calendar: calmer headers, tabular dates/times, more legible shift labels and cards, separate preview styling, existing small-screen agenda retained. Existing overlap layout and date logic unchanged.
- Manual/automatic scheduling: three-step progress indicator for requirements, preview and explicit confirmation; bordered requirement cards, separated selection rows, clearer form controls and sticky dialog actions.
- AI scheduling: dedicated instruction workspace, practical example placeholder, accessible input guidance and character count, separate interpretation result, named clarification and unsupported-rule sections, and visible policy defaults. Required acknowledgment and edit invalidation remain. No automatic preview, apply or publishing.
- Authentication: clearer sign-in copy and form styling, coordinated brand treatment; JWT/session logic unchanged.

## Verification and boundaries

Run npm run lint, npm run typecheck, npm test and npm run build in frontend/. Existing API contract, forms, generation and interpretation regressions remain. The interpretation flow test additionally checks current-step semantics and associated instruction guidance.

No database or paid API calls are needed. No deployment/CD. No commit, push or merge is authorized.

Browser visual verification could not run: the installed computer-use runtime reported “Trusted RPC service is not configured: sky”. Desktop/tablet/mobile CSS was reviewed, but real browser screenshots, contrast measurement, native-dialog behavior, crowded calendars and long-text overflow remain manual review items. Test success is not a substitute for this visual review.

## Recommended review

Review at 1440px desktop, 1024px tablet and 390px mobile. Exercise both roles, empty/error/loading screens, long employee names, crowded calendars and the AI clarification flow using mocked data. Tab through navigation and dialogs, check focus return, and confirm every generation/apply step remains explicit. Consolidate legacy CSS only after approving the direction.
