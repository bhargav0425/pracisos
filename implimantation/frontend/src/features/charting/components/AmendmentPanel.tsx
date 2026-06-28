import { useState } from 'react';
import { useAddAmendmentMutation } from '../api';
import { useAuth } from '../../../shared/hooks/useAuth';
import type { Note } from '../api';

interface Props {
  note: Note;
}

export function AmendmentPanel({ note }: Props) {
  const [text, setText] = useState('');
  const [addAmendment, { isLoading }] = useAddAmendmentMutation();
  const { user } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!text.trim() || !user) return;
    await addAmendment({
      noteId: note.noteId,
      text,
      practitionerId: user.userId,
    }).unwrap();
    setText('');
  };

  return (
    <div className="mt-6 rounded-xl border border-slate-100 bg-slate-50/50 p-5 backdrop-blur-sm">
      <h3 className="mb-4 text-sm font-semibold text-slate-800 flex items-center gap-2">
        <span className="w-1.5 h-4 bg-amber-500 rounded-full" />
        Amendments & Corrections
      </h3>

      {note.amendments.length === 0 && (
        <p className="mb-4 text-sm text-slate-400 italic">No amendments yet.</p>
      )}

      <div className="mb-5 space-y-3">
        {note.amendments.map((a) => (
          <div key={a.amendmentId} className="rounded-lg border border-slate-100 bg-white p-4 shadow-sm transition-all hover:shadow-md">
            <p className="text-sm text-slate-700 whitespace-pre-wrap">{a.amendmentText}</p>
            <div className="mt-2.5 flex items-center justify-between text-[11px] text-slate-400">
              <span>Amended by Practitioner</span>
              <span>{new Date(a.createdAt).toLocaleString()}</span>
            </div>
          </div>
        ))}
      </div>

      <form onSubmit={handleSubmit} className="flex gap-2">
        <input
          type="text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Enter amendment text..."
          disabled={isLoading}
          className="flex-1 rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-sm placeholder-slate-400 transition-colors focus:border-slate-400 focus:outline-none focus:ring-1 focus:ring-slate-400"
        />
        <button
          type="submit"
          disabled={isLoading || !text.trim()}
          className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-medium text-white shadow-sm transition-all hover:bg-amber-600 disabled:opacity-50"
        >
          {isLoading ? 'Adding...' : 'Add'}
        </button>
      </form>
    </div>
  );
}
