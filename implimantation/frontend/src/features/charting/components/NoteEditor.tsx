import { useEffect, useState, useCallback, useRef } from 'react';
import { useSaveDraftMutation, useLockNoteMutation } from '../api';
import { AmendmentPanel } from './AmendmentPanel';
import type { Note, NoteContent } from '../api';

interface Props {
  note: Note;
}

export function NoteEditor({ note }: Props) {
  const [content, setContent] = useState<NoteContent>(note.content);
  const [saveStatus, setSaveStatus] = useState<'saved' | 'saving' | 'unsaved'>('saved');
  const [saveDraft] = useSaveDraftMutation();
  const [lockNote] = useLockNoteMutation();
  const autosaveTimer = useRef<ReturnType<typeof setTimeout>>();
  const isLocked = note.status === 'IMMUTABLE';

  // Synchronize component state when note changes (e.g., when loaded or locked)
  useEffect(() => {
    setContent(note.content);
    setSaveStatus('saved');
  }, [note]);

  const debouncedSave = useCallback(
    async (newContent: NoteContent) => {
      setSaveStatus('saving');
      try {
        await saveDraft({ noteId: note.noteId, content: newContent }).unwrap();
        setSaveStatus('saved');
      } catch {
        setSaveStatus('unsaved');
      }
    },
    [note.noteId, saveDraft]
  );

  useEffect(() => {
    if (isLocked) return;
    
    // Check if content has actually changed from the saved note content
    const hasChanged = 
      content.subjective !== note.content.subjective ||
      content.objective !== note.content.objective ||
      content.assessment !== note.content.assessment ||
      content.plan !== note.content.plan;

    if (!hasChanged) {
      setSaveStatus('saved');
      return;
    }

    if (autosaveTimer.current) clearTimeout(autosaveTimer.current);
    setSaveStatus('unsaved');
    
    autosaveTimer.current = setTimeout(() => {
      debouncedSave(content);
    }, 30000); // 30 second autosave

    return () => {
      if (autosaveTimer.current) clearTimeout(autosaveTimer.current);
    };
  }, [content, debouncedSave, isLocked, note.content]);

  const handleLock = async () => {
    if (!window.confirm('Lock this note? Once locked, it becomes immutable and cannot be edited. Corrections must be added as amendments.')) return;
    try {
      await lockNote(note.noteId).unwrap();
    } catch (err) {
      alert('Failed to lock note: ' + (err as any).data?.detail || 'Error');
    }
  };

  const handleManualSave = async () => {
    await debouncedSave(content);
  };

  if (isLocked) {
    return (
      <div className="space-y-6">
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="mb-5 flex items-center justify-between border-b border-slate-100 pb-4">
            <div className="flex items-center gap-3">
              <span className="rounded-full bg-slate-900 px-3 py-1 text-xs font-semibold text-white tracking-wide">
                LOCKED / IMMUTABLE
              </span>
              <span className="text-xs text-slate-400">
                Locked by {note.lockedBy === 'SYSTEM' ? 'System (24h auto-lock)' : 'Practitioner'} on{' '}
                {new Date(note.lockedAt!).toLocaleString()}
              </span>
            </div>
            <span className="text-xs font-medium text-slate-500 bg-slate-100 px-2.5 py-1 rounded-md">
              {note.appointmentType} Note
            </span>
          </div>
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
            <SOAPEntry label="Subjective (S)" value={content.subjective} />
            <SOAPEntry label="Objective (O)" value={content.objective} />
            <SOAPEntry label="Assessment (A)" value={content.assessment} />
            <SOAPEntry label="Plan (P)" value={content.plan} />
          </div>
        </div>

        <AmendmentPanel note={note} />
      </div>
    );
  }

  return (
    <div className="space-y-5 rounded-xl border border-slate-100 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between border-b border-slate-100 pb-4">
        <div className="flex items-center gap-3">
          <span className={`rounded-full px-3 py-1 text-xs font-semibold tracking-wide transition-all ${
            saveStatus === 'saved' ? 'bg-emerald-50 text-emerald-700' :
            saveStatus === 'saving' ? 'bg-amber-500/10 text-amber-700' :
            'bg-red-500/10 text-red-700'
          }`}>
            {saveStatus === 'saved' ? 'ALL CHANGES SAVED' :
             saveStatus === 'saving' ? 'SAVING DRAFT...' : 'UNSAVED CHANGES'}
          </span>
          <span className="text-xs text-slate-400">
            Auto-saves every 30s
          </span>
        </div>
        <div className="flex gap-2.5">
          <button
            onClick={handleManualSave}
            disabled={saveStatus === 'saving'}
            className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-50 active:bg-slate-100"
          >
            Save Draft
          </button>
          <button
            onClick={handleLock}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white shadow-sm transition-all hover:bg-slate-800 hover:shadow active:bg-black"
          >
            Lock & Sign Note
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
        <SOAPEditor label="Subjective (S)" value={content.subjective}
          placeholder="Patient's complaints, history, and subjective symptoms..."
          onChange={(v) => setContent(c => ({ ...c, subjective: v }))} />
        <SOAPEditor label="Objective (O)" value={content.objective}
          placeholder="Vital signs, physical exam findings, and lab results..."
          onChange={(v) => setContent(c => ({ ...c, objective: v }))} />
        <SOAPEditor label="Assessment (A)" value={content.assessment}
          placeholder="Differential diagnoses, clinical impression, and progress..."
          onChange={(v) => setContent(c => ({ ...c, assessment: v }))} />
        <SOAPEditor label="Plan (P)" value={content.plan}
          placeholder="Treatment plan, medications, referrals, and follow-up..."
          onChange={(v) => setContent(c => ({ ...c, plan: v }))} />
      </div>
    </div>
  );
}

interface EditorProps {
  label: string;
  value: string;
  placeholder: string;
  onChange: (v: string) => void;
}

function SOAPEditor({ label, value, placeholder, onChange }: EditorProps) {
  return (
    <div className="flex flex-col">
      <label className="mb-1.5 text-xs font-bold uppercase tracking-wider text-slate-500">{label}</label>
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        rows={6}
        className="w-full resize-none rounded-lg border border-slate-200 p-3.5 text-sm placeholder-slate-400 transition-colors focus:border-slate-400 focus:outline-none focus:ring-1 focus:ring-slate-400"
        placeholder={placeholder}
      />
    </div>
  );
}

function SOAPEntry({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-50 bg-slate-50/50 p-4">
      <h4 className="mb-1.5 text-xs font-bold uppercase tracking-wider text-slate-400">{label}</h4>
      <p className="whitespace-pre-wrap text-sm text-slate-700 leading-relaxed">{value || <em className="text-slate-300">No entry</em>}</p>
    </div>
  );
}
