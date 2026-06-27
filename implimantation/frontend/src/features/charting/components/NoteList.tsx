import { useState, useEffect } from 'react';
import { useGetNotesByPatientQuery } from '../api';
import { NoteEditor } from './NoteEditor';
import { FileText, Calendar, Lock, Edit3, ChevronRight, Inbox } from 'lucide-react';
import type { Note } from '../api';

interface Props {
  patientId: string;
  patientName?: string;
}

export function NoteList({ patientId, patientName }: Props) {
  const { data: notes, isLoading, error, refetch } = useGetNotesByPatientQuery(patientId);
  const [selectedNote, setSelectedNote] = useState<Note | null>(null);

  // Auto-select the first note when loaded or updated
  useEffect(() => {
    if (notes && notes.length > 0) {
      // If we already have a selected note, find its updated version in the list
      if (selectedNote) {
        const updated = notes.find(n => n.noteId === selectedNote.noteId);
        if (updated) {
          setSelectedNote(updated);
          return;
        }
      }
      setSelectedNote(notes[0]);
    } else {
      setSelectedNote(null);
    }
  }, [notes]);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center gap-3">
        <span className="h-5 w-5 animate-spin rounded-full border-2 border-slate-600 border-t-transparent" />
        <span className="text-sm font-medium text-slate-500">Loading clinical history...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-red-100 bg-red-50 p-4 text-sm text-red-600">
        Failed to load clinical notes: {(error as any).data?.detail || 'Verify your permissions.'}
      </div>
    );
  }

  if (!notes || notes.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50/50 py-12 px-4 text-center">
        <div className="rounded-full bg-slate-100 p-3 text-slate-400">
          <Inbox className="h-6 h-6" />
        </div>
        <h3 className="mt-4 text-sm font-semibold text-slate-800">No Clinical Notes</h3>
        <p className="mt-1.5 text-xs text-slate-400 max-w-xs leading-relaxed">
          Clinical notes are automatically generated when an appointment is booked. No appointments or notes found for {patientName || 'this patient'}.
        </p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
      {/* Sidebar - Note List */}
      <div className="lg:col-span-4 space-y-3">
        <div className="flex items-center justify-between px-1">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Clinical Record ({notes.length})</span>
          <button 
            onClick={() => refetch()} 
            className="text-[11px] font-medium text-slate-500 hover:text-slate-800"
          >
            Refresh
          </button>
        </div>
        <div className="max-h-[600px] overflow-y-auto space-y-2 pr-1">
          {notes.map((note) => {
            const isSelected = selectedNote?.noteId === note.noteId;
            const noteDate = new Date(note.createdAt).toLocaleDateString(undefined, {
              month: 'short',
              day: 'numeric',
              year: 'numeric'
            });

            return (
              <button
                key={note.noteId}
                onClick={() => setSelectedNote(note)}
                className={`w-full text-left rounded-xl border p-4 transition-all duration-200 flex items-start gap-3.5 ${
                  isSelected
                    ? 'border-slate-800 bg-slate-50 shadow-sm'
                    : 'border-slate-100 bg-white hover:border-slate-200 hover:bg-slate-50/30'
                }`}
              >
                <div className={`rounded-lg p-2 ${isSelected ? 'bg-slate-900 text-white' : 'bg-slate-50 text-slate-400'}`}>
                  <FileText className="h-4 w-4" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-semibold text-slate-800 truncate">{note.appointmentType}</span>
                    <span className={`inline-flex items-center gap-1 text-[10px] font-bold px-1.5 py-0.5 rounded-full ${
                      note.status === 'IMMUTABLE' 
                        ? 'bg-slate-100 text-slate-600' 
                        : 'bg-emerald-50 text-emerald-700'
                    }`}>
                      {note.status === 'IMMUTABLE' ? <Lock className="w-2 h-2" /> : <Edit3 className="w-2 h-2" />}
                      {note.status}
                    </span>
                  </div>
                  <div className="mt-1.5 flex items-center gap-1 text-[11px] text-slate-400">
                    <Calendar className="w-3 h-3" />
                    <span>{noteDate}</span>
                  </div>
                </div>
                <ChevronRight className={`w-4 h-4 mt-2 self-start text-slate-300 transition-transform ${isSelected ? 'translate-x-0.5 text-slate-500' : ''}`} />
              </button>
            );
          })}
        </div>
      </div>

      {/* Main Content - Selected Note Editor/Viewer */}
      <div className="lg:col-span-8">
        {selectedNote ? (
          <NoteEditor note={selectedNote} />
        ) : (
          <div className="flex h-full min-h-[300px] items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50/50 p-6 text-center">
            <p className="text-sm text-slate-400">Select a note from the history to view or edit.</p>
          </div>
        )}
      </div>
    </div>
  );
}
