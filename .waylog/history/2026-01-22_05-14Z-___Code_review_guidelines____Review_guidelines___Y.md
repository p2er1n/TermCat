# ## Code review guidelines:
# Review guidelines:

Y
_Exported on 01/22/2026 at 13:14:50 GMT+8 from OpenAI Codex via WayLog_


**OpenAI Codex**

<permissions instructions>Filesystem sandboxing defines which files can be read or written. `sandbox_mode` is `read-only`: The sandbox only permits reading files. Network access is restricted. Approvals are your mechanism to get user consent to run shell commands without the sandbox. `approval_policy` is `on-request`: Commands will be run in the sandbox by default, and you can specify in your tool call if you want to escalate a command to run without sandboxing. If the completing the task requires escalated permissions, Do not let these settings or the sandbox deter you from attempting to accomplish the user's task.

Here are scenarios where you'll need to request approval:
- You need to run a command that writes to a directory that requires it (e.g. running tests that write to /var)
- You need to run a GUI app (e.g., open/xdg-open/osascript) to open browsers or files.
- You are running sandboxed and need to run a command that requires network access (e.g. installing packages)
- If you run a command that is important to solving the user's query, but it fails because of sandboxing, rerun the command with approval. ALWAYS proceed to use the `sandbox_permissions` and `justification` parameters - do not message the user before requesting approval for the command.
- You are about to take a potentially destructive action such as an `rm` or `git reset` that the user did not explicitly ask for.

When requesting approval to execute a command that will require escalated privileges:
  - Provide the `sandbox_permissions` parameter with the value `"require_escalated"`
  - Include a short, 1 sentence explanation for why you need escalated permissions in the justification parameter</permissions instructions>

---


**OpenAI Codex**

<permissions instructions>Filesystem sandboxing defines which files can be read or written. `sandbox_mode` is `workspace-write`: The sandbox permits reading files, and editing files in `cwd` and `writable_roots`. Editing files in other directories requires approval. Network access is restricted. Approvals are your mechanism to get user consent to run shell commands without the sandbox. `approval_policy` is `on-request`: Commands will be run in the sandbox by default, and you can specify in your tool call if you want to escalate a command to run without sandboxing. If the completing the task requires escalated permissions, Do not let these settings or the sandbox deter you from attempting to accomplish the user's task.

Here are scenarios where you'll need to request approval:
- You need to run a command that writes to a directory that requires it (e.g. running tests that write to /var)
- You need to run a GUI app (e.g., open/xdg-open/osascript) to open browsers or files.
- You are running sandboxed and need to run a command that requires network access (e.g. installing packages)
- If you run a command that is important to solving the user's query, but it fails because of sandboxing, rerun the command with approval. ALWAYS proceed to use the `sandbox_permissions` and `justification` parameters - do not message the user before requesting approval for the command.
- You are about to take a potentially destructive action such as an `rm` or `git reset` that the user did not explicitly ask for.

When requesting approval to execute a command that will require escalated privileges:
  - Provide the `sandbox_permissions` parameter with the value `"require_escalated"`
  - Include a short, 1 sentence explanation for why you need escalated permissions in the justification parameter The writable roots are `C:/Users/user/AndroidStudioProjects/TermCat`, `c:\Users\user\AndroidStudioProjects\TermCat`, `c:\Users\user\AndroidStudioProjects\TermCat`.</permissions instructions>

---


**OpenAI Codex**

<permissions instructions>Filesystem sandboxing defines which files can be read or written. `sandbox_mode` is `workspace-write`: The sandbox permits reading files, and editing files in `cwd` and `writable_roots`. Editing files in other directories requires approval. Network access is restricted. Approvals are your mechanism to get user consent to run shell commands without the sandbox. `approval_policy` is `on-request`: Commands will be run in the sandbox by default, and you can specify in your tool call if you want to escalate a command to run without sandboxing. If the completing the task requires escalated permissions, Do not let these settings or the sandbox deter you from attempting to accomplish the user's task.

Here are scenarios where you'll need to request approval:
- You need to run a command that writes to a directory that requires it (e.g. running tests that write to /var)
- You need to run a GUI app (e.g., open/xdg-open/osascript) to open browsers or files.
- You are running sandboxed and need to run a command that requires network access (e.g. installing packages)
- If you run a command that is important to solving the user's query, but it fails because of sandboxing, rerun the command with approval. ALWAYS proceed to use the `sandbox_permissions` and `justification` parameters - do not message the user before requesting approval for the command.
- You are about to take a potentially destructive action such as an `rm` or `git reset` that the user did not explicitly ask for.

When requesting approval to execute a command that will require escalated privileges:
  - Provide the `sandbox_permissions` parameter with the value `"require_escalated"`
  - Include a short, 1 sentence explanation for why you need escalated permissions in the justification parameter The writable roots are `C:/Users/user/AndroidStudioProjects/TermCat`, `c:\Users\user\AndroidStudioProjects\TermCat`, `c:\Users\user\AndroidStudioProjects\TermCat`.</permissions instructions>

---


**User**

## Code review guidelines:
# Review guidelines:

You are acting as a reviewer for a proposed code change made by another engineer.

Below are some default guidelines for determining whether the original author would appreciate the issue being flagged.

These are not the final word in determining whether an issue is a bug. In many cases, you will encounter other, more specific guidelines. These may be present elsewhere in a developer message, a user message, a file, or even elsewhere in this system message.
Those guidelines should be considered to override these general instructions.

Here are the general guidelines for determining whether something is a bug and should be flagged.

1. It meaningfully impacts the accuracy, performance, security, or maintainability of the code.
2. The bug is discrete and actionable (i.e. not a general issue with the codebase or a combination of multiple issues).
3. Fixing the bug does not demand a level of rigor that is not present in the rest of the codebase (e.g. one doesn't need very detailed comments and input validation in a repository of one-off scripts in personal projects)
4. The bug was introduced in the commit (pre-existing bugs should not be flagged).
5. The author of the original PR would likely fix the issue if they were made aware of it.
6. The bug does not rely on unstated assumptions about the codebase or author's intent.
7. It is not enough to speculate that a change may disrupt another part of the codebase, to be considered a bug, one must identify the other parts of the code that are provably affected.
8. The bug is clearly not just an intentional change by the original author.

When flagging a bug, you will also provide an accompanying comment. Once again, these guidelines are not the final word on how to construct a comment -- defer to any subsequent guidelines that you encounter.

1. The comment should be clear about why the issue is a bug.
2. The comment should appropriately communicate the severity of the issue. It should not claim that an issue is more severe than it actually is.
3. The comment should be brief. The body should be at most 1 paragraph. It should not introduce line breaks within the natural language flow unless it is necessary for the code fragment.
4. The comment should not include any chunks of code longer than 3 lines. Any code chunks should be wrapped in markdown inline code tags or a code block.
5. The comment should clearly and explicitly communicate the scenarios, environments, or inputs that are necessary for the bug to arise. The comment should immediately indicate that the issue's severity depends on these factors.
6. The comment's tone should be matter-of-fact and not accusatory or overly positive. It should read as a helpful AI assistant suggestion without sounding too much like a human reviewer.
7. The comment should be written such that the original author can immediately grasp the idea without close reading.
8. The comment should avoid excessive flattery and comments that are not helpful to the original author. The comment should avoid phrasing like "Great job ...", "Thanks for ...".

Below are some more detailed guidelines that you should apply to this specific review.

HOW MANY FINDINGS TO RETURN:

Output all findings that the original author would fix if they knew about it. If there is no finding that a person would definitely love to see and fix, prefer outputting no findings. Do not stop at the first qualifying finding. Continue until you've listed every qualifying finding.

GUIDELINES:

- Ignore trivial style unless it obscures meaning or violates documented standards.
- Use one comment per distinct issue (or a multi-line range if necessary).
- Use ```suggestion blocks ONLY for concrete replacement code (minimal lines; no commentary inside the block).
- In every ```suggestion block, preserve the exact leading whitespace of the replaced lines (spaces vs tabs, number of spaces).
- Do NOT introduce or remove outer indentation levels unless that is the actual fix.

The comments will be presented in the code review as inline comments. You should avoid providing unnecessary location details in the comment body. Always keep the line range as short as possible for interpreting the issue. Avoid ranges longer than 5–10 lines; instead, choose the most suitable subrange that pinpoints the problem.

At the beginning of the finding title, tag the bug with priority level. For example "[P1] Un-padding slices along wrong tensor dimensions". [P0] – Drop everything to fix. Blocking release, operations, or major usage. Only use for universal issues that do not depend on any assumptions about the inputs. · [P1] – Urgent. Should be addressed in the next cycle · [P2] – Normal. To be fixed eventually · [P3] – Low. Nice to have.

Additionally, include a numeric priority field in the JSON output for each finding: set "priority" to 0 for P0, 1 for P1, 2 for P2, or 3 for P3. If a priority cannot be determined, omit the field or use null.

At the end of your findings, output an "overall correctness" verdict of whether or not the patch should be considered "correct".
Correct implies that existing code and tests will not break, and the patch is free of bugs and other blocking issues.
Ignore non-blocking issues such as style, formatting, typos, documentation, and other nits.

FORMATTING GUIDELINES:
The finding description should be one paragraph.

OUTPUT FORMAT:

## Output schema — MUST MATCH _exactly_

```json
{
  "findings": [
    {
      "title": "<≤ 80 chars, imperative>",
      "body": "<valid Markdown explaining *why* this is a problem; cite files/lines/functions>",
      "confidence_score": <float 0.0-1.0>,
      "priority": <int 0-3, optional>,
      "code_location": {
        "absolute_file_path": "<file path>",
        "line_range": {"start": <int>, "end": <int>}
      }
    }
  ],
  "overall_correctness": "patch is correct" | "patch is incorrect",
  "overall_explanation": "<1-3 sentence explanation justifying the overall_correctness verdict>",
  "overall_confidence_score": <float 0.0-1.0>
}
```

- **Do not** wrap the JSON in markdown fences or extra prose.
- The code_location field is required and must include absolute_file_path and line_range.
  \*Line ranges must be as short as possible for interpreting the issue (avoid ranges over 5–10 lines; pick the most suitable subrange).
- The code_location should overlap with the diff.
- Do not generate a PR fix.
- Your **initial** response to the user must always use the JSON schema above.
- After the initial response, choose the format based on the user's request:
  - If they explicitly ask for another review ("re-review", "rerun the review", etc.), return the findings in the exact JSON schema again.
  - If they ask you to fix something, answer follow-up questions, or otherwise request non-review help, respond in plain text (no JSON or schema output).
- All review instructions apply to the overall code review conversation. Continue following the guidelines in every response, only switching between JSON and plain text as described above.
Review the code changes against the base branch 'main'. The merge base commit for this comparison is 66e694de3b1ddf8ee1a5627c2aa471478983f9f1. Run `git diff 66e694de3b1ddf8ee1a5627c2aa471478983f9f1` to inspect the changes relative to main. Provide prioritized, actionable findings.
## My request for Codex:
Please review changes on main against main

---


**OpenAI Codex**

{
  "findings": [
    {
      "title": "[P2] Allow single-page capture without accessibility",
      "body": "The new flow hard-blocks capture when the accessibility service is disabled, even though accessibility is only needed for auto-scrolling. This means users who previously could capture a single page now always get an error and no OCR unless they enable the service. If the service is meant to be optional, this is a functional regression; consider capturing at least one page when accessibility is off and only gating the scroll loop.",
      "confidence_score": 0.61,
      "priority": 2,
      "code_location": {
        "absolute_file_path": "c:\\Users\\user\\AndroidStudioProjects\\TermCat\\app\\src\\main\\java\\org\\p2er1n\\termcat\\ScreenshotService.kt",
        "line_range": {
          "start": 147,
          "end": 159
        }
      }
    },
    {
      "title": "[P2] Avoid logging OCR text to logcat",
      "body": "The new logging prints captured OCR text to Logcat, which can include sensitive screen content. On debug builds (or devices with log access), this exposes user data in clear text. If you need diagnostics, consider guarding this behind a debug flag or logging only metadata (length/empty) instead of content.",
      "confidence_score": 0.49,
      "priority": 2,
      "code_location": {
        "absolute_file_path": "c:\\Users\\user\\AndroidStudioProjects\\TermCat\\app\\src\\main\\java\\org\\p2er1n\\termcat\\ScreenshotService.kt",
        "line_range": {
          "start": 132,
          "end": 132
        }
      }
    }
  ],
  "overall_correctness": "patch is incorrect",
  "overall_explanation": "The change introduces a functional regression by requiring accessibility for any capture, not just auto-scroll, and it also logs OCR content to Logcat, which is a sensitive data exposure. These issues mean the patch is not safe to consider correct as-is.",
  "overall_confidence_score": 0.57
}

---
