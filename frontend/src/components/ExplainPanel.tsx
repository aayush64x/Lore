import { Link } from "react-router-dom";
import { conceptsUrl } from "../api/client";
import type { ChangeResponse, ExplanationData, QuestionResponse } from "../api/client";
import ExplainWalkthrough from "./ExplainWalkthrough";
import FollowUp from "./FollowUp";
import NoteBox from "./NoteBox";

interface ExplainPanelProps {
  change: ChangeResponse;
  project: string; // for the concept chips' links to the Concepts page
  // Which change this is in the file, for "Change 2 of 3".
  index: number;
  count: number;
  onPrevChange: () => void;
  onNextChange: () => void;
  explanation: ExplanationData | null;
  loading: boolean;
  error: string | null;
  onExplain: () => void;
  activeStep: number;
  onActivateStep: (i: number) => void;
  onHoverStep: (i: number | null) => void;
  stepLabel: (diffStart: number, diffEnd: number) => string;
  // Undo a self-mark (a pass can't be undone here).
  onToggleUnderstood: () => void;
  // "Got it? Mark as understood" at the last step, and "Skip test": marks it (self) and moves on.
  onMarkUnderstood: () => void;
  onTestMe: () => void;
  // "Explain again to enable testing": a new explanation with key points.
  onExplainForTest: () => void;
  // A follow-up question was answered (or failed: null).
  onQuestionAsked: (asked: QuestionResponse | null) => void;
  // The note was saved (null = cleared).
  onNoteSaved: (note: string | null) => void;
}

const LINK = "text-xs text-gray-500 hover:text-gray-800 underline";

// The right side of the session page: summary, status, what to do next (test, skip,
// explain), and the step-by-step explanation for the selected change.
export default function ExplainPanel({
  change,
  project,
  index,
  count,
  onPrevChange,
  onNextChange,
  explanation,
  loading,
  error,
  onExplain,
  activeStep,
  onActivateStep,
  onHoverStep,
  stepLabel,
  onToggleUnderstood,
  onMarkUnderstood,
  onTestMe,
  onExplainForTest,
  onQuestionAsked,
  onNoteSaved,
}: ExplainPanelProps) {
  const passed = change.understoodBy === "teach_back";
  const canTest = explanation?.testable === true && !!explanation.hasKeyPoints;
  // Made before key points existed: testing needs a new explanation (only when asked).
  // (When the "Explain again" note above the steps is showing, that one does it already.)
  const madeBeforeTesting = !!explanation && explanation.testable == null && explanation.current !== false;
  const nothingToTest = explanation?.testable === false;
  const skipLink = !change.understood && (
    <button onClick={onMarkUnderstood} className={`block mt-2 ${LINK}`}>
      Skip test and mark it understood
    </button>
  );

  return (
    <div className="p-5">
      {count > 1 && (
        <div className="flex items-center justify-between mb-4 text-xs text-gray-500">
          <button onClick={onPrevChange} disabled={index === 0} className="hover:text-gray-900 disabled:text-gray-300">
            ← Prev change
          </button>
          <span>
            Change {index + 1} of {count}
          </span>
          <button onClick={onNextChange} disabled={index === count - 1} className="hover:text-gray-900 disabled:text-gray-300">
            Next change →
          </button>
        </div>
      )}

      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide pt-1">Change summary</div>
        {passed ? (
          <span className="text-xs font-medium px-2.5 py-1 rounded-full border whitespace-nowrap bg-green-600 text-white border-green-600">
            ✓ Passed
          </span>
        ) : change.understood ? (
          <button
            onClick={onToggleUnderstood}
            title="Marked by you, not tested. Click to undo."
            className="text-xs font-medium px-2.5 py-1 rounded-full border whitespace-nowrap bg-green-50 text-green-700 border-green-300 hover:bg-green-100"
          >
            ✓ Self-marked
          </button>
        ) : (
          <span className="text-xs font-medium px-2.5 py-1 rounded-full border whitespace-nowrap bg-orange-50 text-orange-700 border-orange-200">
            Not understood yet
          </span>
        )}
      </div>
      <p className="text-sm text-gray-500 mb-4">{change.description ?? "No summary for this change."}</p>

      {change.concepts.length > 0 && (
        // What you'd learn from this change; each opens its row on the Concepts page.
        <div className="-mt-2 mb-4 flex flex-wrap gap-1.5" aria-label="Concepts">
          {change.concepts.map((c) => (
            <Link
              key={c.name}
              to={conceptsUrl(project, c.other ? "suggested" : c.name)}
              title={c.other ? "Suggested by the AI, not on your concept list" : "See this concept across your sessions"}
              className={`text-xs px-2 py-0.5 rounded-full border ${
                c.other
                  ? "border-dashed border-gray-300 text-gray-500 hover:text-gray-800"
                  : "border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100"
              }`}
            >
              {c.other ? `Other: ${c.name}` : c.name}
            </Link>
          ))}
        </div>
      )}

      {passed && canTest && (
        <button onClick={onTestMe} className={`-mt-2 mb-4 ${LINK}`}>
          Retake (practice)
        </button>
      )}

      {!passed && !change.binary && canTest && (
        <div className="mb-4">
          <button
            onClick={onTestMe}
            className="w-full text-sm font-medium py-2.5 rounded-md bg-blue-600 text-white hover:bg-blue-700 transition-colors"
          >
            {change.teachBack.attemptsLeft === 0 ? "See the key points" : "Test me"}
          </button>
          {change.teachBack.attemptsLeft > 0 && skipLink}
        </div>
      )}
      {!passed && madeBeforeTesting && (
        <div className="mb-4 px-3 py-2 rounded-md bg-gray-50 border border-gray-200 text-xs text-gray-600">
          <button
            onClick={onExplainForTest}
            disabled={loading}
            className="font-medium text-blue-600 hover:underline disabled:text-gray-400 disabled:no-underline"
          >
            {loading ? "Explaining…" : "Explain again to enable testing"}
          </button>
          {skipLink}
        </div>
      )}
      {nothingToTest && !change.understood && (
        <div className="mb-4 text-xs text-gray-500">
          Nothing to test here (formatting, imports or renames).{" "}
          <button onClick={onMarkUnderstood} className="font-medium text-blue-600 hover:underline">
            Mark as understood
          </button>
        </div>
      )}

      {!change.binary && !explanation && (
        <>
          <button
            onClick={onExplain}
            disabled={loading}
            className="w-full text-sm font-medium py-2.5 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:bg-blue-300 transition-colors"
          >
            {loading ? "Explaining…" : "Explain this"}
          </button>
          {error && <p className="text-sm text-red-600 mt-2">{error} Click the button to try again.</p>}
          {skipLink}
        </>
      )}

      {explanation && (
        <div className="border-t border-gray-100 pt-4">
          {explanation.current === false && (
            <div className="flex items-center justify-between gap-3 mb-3 px-3 py-2 rounded-md bg-gray-50 border border-gray-200 text-xs text-gray-600">
              <span>
                {explanation.damaged
                  ? "This explanation came back garbled when it was made."
                  : `Written for ${explanation.writtenFor ?? "another level"}`}
              </span>
              <button
                onClick={onExplain}
                disabled={loading}
                className="font-medium text-blue-600 hover:underline disabled:text-gray-400 disabled:no-underline whitespace-nowrap"
              >
                {loading ? "Explaining…" : "Explain again"}
              </button>
            </div>
          )}
          {explanation.current === false && error && <p className="text-xs text-red-600 mb-3">{error}</p>}
          <ExplainWalkthrough
            explanation={explanation}
            active={activeStep}
            onActivate={onActivateStep}
            onHoverStep={onHoverStep}
            stepLabel={stepLabel}
            understood={change.understood}
            understoodBy={change.understoodBy}
            onMarkUnderstood={onMarkUnderstood}
            onTestMe={canTest && change.teachBack.attemptsLeft > 0 ? onTestMe : undefined}
          />
          <FollowUp key={change.id} changeId={change.id} questions={change.questions} onAsked={onQuestionAsked} />
          <p className="text-xs text-gray-400 mt-4">Keys: ← → steps · [ ] files · b sidebar</p>
        </div>
      )}

      <NoteBox key={change.id} changeId={change.id} note={change.note} onSaved={onNoteSaved} />
    </div>
  );
}
