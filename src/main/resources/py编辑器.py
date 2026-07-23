#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
ADV NPC EpicFight 配置编辑器
支持拖拽JSON文件，可视化修改/新建NPC战斗配置
依赖: pip install tkinterdnd2
"""

import json
import tkinter as tk
from tkinter import ttk, filedialog, messagebox, simpledialog
from pathlib import Path
from copy import deepcopy

try:
    from tkinterdnd2 import DND_FILES, TkinterDnD
    HAS_DND = True
except ImportError:
    HAS_DND = False


TEMPLATE = {
    "model": "epicfight:entity/biped",
    "armature": "epicfight:entity/biped",
    "renderer": "epicfight:custom",
    "isHumanoid": True,
    "faction": "villager",
    "attributes": {
        "impact": 2.0,
        "armor_negation": 0.0,
        "max_strikes": 1,
        "chasing_speed": 1.0,
        "scale": 1.0
    },
    "default_livingmotions": {
        "idle": "epicfight:biped/living/idle",
        "walk": "epicfight:biped/living/walk",
        "chase": "epicfight:biped/living/walk",
        "mount": "epicfight:biped/living/mount",
        "fall": "epicfight:biped/living/fall",
        "death": "epicfight:biped/living/death"
    },
    "stun_animations": {
        "short": "epicfight:biped/combat/hit_short",
        "long": "epicfight:biped/combat/hit_long",
        "knockdown": "epicfight:biped/combat/knockdown",
        "fall": "epicfight:biped/living/landing",
        "neutralize": "epicfight:biped/skill/guard_break1"
    },
    "combat_behavior": []
}

RENDERER_OPTIONS = ["epicfight:custom", "zombie", "skeleton"]
FACTION_OPTIONS = ["villager", "undead"]


def flatten_dict(d, parent_key=""):
    items = []
    if isinstance(d, dict):
        for k, v in d.items():
            new_key = f"{parent_key}.{k}" if parent_key else k
            if isinstance(v, (dict, list)):
                items.append((new_key, type(v).__name__, v))
                items.extend(flatten_dict(v, new_key))
            else:
                items.append((new_key, type(v).__name__, v))
    elif isinstance(d, list):
        for i, v in enumerate(d):
            new_key = f"{parent_key}[{i}]"
            if isinstance(v, (dict, list)):
                items.append((new_key, type(v).__name__, v))
                items.extend(flatten_dict(v, new_key))
            else:
                items.append((new_key, type(v).__name__, v))
    return items


def recreate_structure(path_parts, value, current):
    """Helper: set nested dict/list value from dot-path parts."""
    key = path_parts[0]
    if len(path_parts) == 1:
        if isinstance(current, dict):
            current[key] = value
        elif isinstance(current, list):
            idx = int(key.strip("[]"))
            current[idx] = value
        return
    next_key = path_parts[1]
    is_list_next = "[" in next_key
    if isinstance(current, dict):
        if key not in current:
            current[key] = [] if is_list_next else {}
        recreate_structure(path_parts[1:], value, current[key])
    elif isinstance(current, list):
        idx = int(key.strip("[]"))
        if idx >= len(current):
            current.append([] if is_list_next else {})
        recreate_structure(path_parts[1:], value, current[idx])


def value_from_path(path, data):
    parts = path.replace("]", "").split(".")
    current = data
    for part in parts:
        if "[" in part:
            p, idx = part.split("[")
            if isinstance(current, dict) and p:
                current = current.get(p, {})
            if isinstance(current, list):
                current = current[int(idx)]
        else:
            if isinstance(current, dict):
                current = current.get(part, {})
            elif isinstance(current, list):
                current = current[int(part)]
    return current


def set_value_at_path(path, data, value):
    parts = path.replace("]", "").split(".")
    current = data
    for i, part in enumerate(parts[:-1]):
        if "[" in part:
            p, idx = part.split("[")
            if isinstance(current, dict) and p:
                current = current.get(p, {})
            if isinstance(current, list):
                current = current[int(idx)]
        else:
            if isinstance(current, dict):
                current = current.get(part, {})
            elif isinstance(current, list):
                current = current[int(part)]
    last = parts[-1]
    if "[" in last:
        p, idx = last.split("[")
        if isinstance(current, dict):
            if isinstance(current.get(p), list):
                current[p][int(idx)] = value
            else:
                current[p] = [value]
        elif isinstance(current, list):
            current[int(idx)] = value
    else:
        if isinstance(current, dict):
            current[last] = value
        elif isinstance(current, list):
            current[int(last)] = value


class JSONTreeViewer:
    def __init__(self, parent, data_changed_callback=None):
        self.parent = parent
        self.data_changed = data_changed_callback
        self.tree = None
        self._data = {}
        self._path_map = {}

    def build(self, parent_frame):
        frame = ttk.Frame(parent_frame)
        frame.pack(fill=tk.BOTH, expand=True)

        tree_frame = ttk.Frame(frame)
        tree_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        vsb = ttk.Scrollbar(tree_frame, orient=tk.VERTICAL)
        hsb = ttk.Scrollbar(tree_frame, orient=tk.HORIZONTAL)

        self.tree = ttk.Treeview(
            tree_frame,
            columns=("value", "type"),
            show="tree headings",
            yscrollcommand=vsb.set,
            xscrollcommand=hsb.set
        )
        self.tree.heading("#0", text="Key/Path")
        self.tree.heading("value", text="Value")
        self.tree.heading("type", text="Type")
        self.tree.column("#0", width=350, minwidth=200)
        self.tree.column("value", width=250, minwidth=100)
        self.tree.column("type", width=80, minwidth=60)

        vsb.config(command=self.tree.yview)
        hsb.config(command=self.tree.xview)
        vsb.pack(side=tk.RIGHT, fill=tk.Y)
        hsb.pack(side=tk.BOTTOM, fill=tk.X)
        self.tree.pack(fill=tk.BOTH, expand=True)

        editor_frame = ttk.Frame(frame, width=350)
        editor_frame.pack(side=tk.RIGHT, fill=tk.Y, padx=(5, 0))
        ttk.Label(editor_frame, text="属性编辑", font=("", 11, "bold")).pack(anchor=tk.W)
        self.editor_key_label = ttk.Label(editor_frame, text="", wraplength=330)
        self.editor_key_label.pack(anchor=tk.W, pady=2)
        self.editor_type_label = ttk.Label(editor_frame, text="")
        self.editor_type_label.pack(anchor=tk.W)

        self.editor_entry = ttk.Entry(editor_frame, width=40)
        self.editor_entry.pack(fill=tk.X, pady=5)
        self.editor_btn = ttk.Button(editor_frame, text="应用修改", command=self.apply_edit)
        self.editor_btn.pack(anchor=tk.W, pady=2)

        self.editor_help = ttk.Label(editor_frame, text="双击树节点快速编辑", foreground="gray", wraplength=330)
        self.editor_help.pack(anchor=tk.W, pady=2)

        self.tree.bind("<<TreeviewSelect>>", self.on_select)
        self.tree.bind("<Double-1>", self.on_double_click)

        self._editor_frame = editor_frame
        return frame

    def load_data(self, data):
        self._data = data
        self._path_map = {}
        self.tree.delete(*self.tree.get_children())
        self._populate_tree("", data, "")

    def _populate_tree(self, parent_id, data, path):
        if isinstance(data, dict):
            items = list(data.items())
            items.sort(key=lambda x: (0 if x[0] == "weapon_categories" else
                                      1 if x[0] == "style" else
                                      2 if x[0] == "weight" else 3))
            for key, value in items:
                child_path = f"{path}.{key}" if path else key
                child_id = self.tree.insert(parent_id, tk.END,
                                            text=key, values=(self._short_val(value), type(value).__name__),
                                            open=True)
                self._path_map[child_id] = child_path
                if isinstance(value, (dict, list)):
                    self._populate_tree(child_id, value, child_path)
        elif isinstance(data, list):
            for i, value in enumerate(data):
                child_path = f"{path}[{i}]"
                display_key = self._list_item_key(value, i)
                child_id = self.tree.insert(parent_id, tk.END,
                                            text=display_key,
                                            values=(self._short_val(value), type(value).__name__),
                                            open=True)
                self._path_map[child_id] = child_path
                if isinstance(value, (dict, list)):
                    self._populate_tree(child_id, value, child_path)

    def _list_item_key(self, value, index):
        if isinstance(value, dict):
            if "weapon_categories" in value and "style" in value:
                cats = "/".join(value["weapon_categories"])
                return f"[{index}] {cats} ({value['style']})"
            if "animation" in value:
                anim = value["animation"].split("/")[-1] if "/" in value["animation"] else value["animation"]
                return f"[{index}] {anim}"
            if "predicate" in value:
                return f"[{index}] {value.get('predicate','?')}"
            return f"[{index}]"
        return f"[{index}] = {self._short_val(value)}"

    def _short_val(self, value):
        s = str(value)
        return s[:60] + "..." if len(s) > 60 else s

    def on_select(self, event):
        sel = self.tree.selection()
        if not sel:
            return
        item = sel[0]
        path = self._path_map.get(item, "")
        self.editor_key_label.config(text=f"路径: {path}" if path else "(root)")
        try:
            val = value_from_path(path, self._data)
            vtype = type(val).__name__
            self.editor_type_label.config(text=f"类型: {vtype}")
            if isinstance(val, (dict, list)):
                self.editor_entry.delete(0, tk.END)
                self.editor_entry.insert(0, f"<{vtype}> 不可直接编辑，展开子项修改")
                self.editor_entry.config(state=tk.DISABLED)
            else:
                self.editor_entry.config(state=tk.NORMAL)
                self.editor_entry.delete(0, tk.END)
                self.editor_entry.insert(0, str(val))
        except:
            self.editor_entry.delete(0, tk.END)
            self.editor_entry.config(state=tk.DISABLED)

    def on_double_click(self, event):
        sel = self.tree.selection()
        if not sel:
            return
        item = sel[0]
        path = self._path_map.get(item, "")
        try:
            val = value_from_path(path, self._data)
        except:
            return
        if isinstance(val, (dict, list)):
            return
        new_val = simpledialog.askstring("编辑值", f"路径: {path}\n当前值: {val}", initialvalue=str(val))
        if new_val is None:
            return
        try:
            if isinstance(val, bool):
                parsed = new_val.lower() in ("true", "1", "yes")
            elif isinstance(val, int):
                parsed = int(new_val)
            elif isinstance(val, float):
                parsed = float(new_val)
            else:
                parsed = new_val
            set_value_at_path(path, self._data, parsed)
            self.load_data(self._data)
            if self.data_changed:
                self.data_changed()
        except (ValueError, Exception) as e:
            messagebox.showerror("错误", f"值无效: {e}")

    def apply_edit(self):
        sel = self.tree.selection()
        if not sel:
            return
        item = sel[0]
        path = self._path_map.get(item, "")
        if not path:
            return
        try:
            val = value_from_path(path, self._data)
        except:
            return
        if isinstance(val, (dict, list)):
            return
        new_val = self.editor_entry.get()
        try:
            if isinstance(val, bool):
                parsed = new_val.lower() in ("true", "1", "yes")
            elif isinstance(val, int):
                parsed = int(new_val)
            elif isinstance(val, float):
                parsed = float(new_val)
            else:
                parsed = new_val
            set_value_at_path(path, self._data, parsed)
            self.load_data(self._data)
            if self.data_changed:
                self.data_changed()
        except (ValueError, Exception) as e:
            messagebox.showerror("错误", f"值无效: {e}")


class EditorApp:
    def __init__(self, root):
        self.root = root
        self.root.title("ADV NPC EpicFight 配置编辑器")
        self.root.geometry("1100x700")
        self.current_file = None
        self.data = deepcopy(TEMPLATE)
        self.modified = False

        self._build_ui()
        self._setup_drag_drop()
        self.load_data(self.data)

    def _build_ui(self):
        # Toolbar
        toolbar = ttk.Frame(self.root)
        toolbar.pack(fill=tk.X, padx=5, pady=3)

        ttk.Button(toolbar, text="打开文件", command=self.open_file).pack(side=tk.LEFT, padx=2)
        ttk.Button(toolbar, text="新建配置", command=self.new_file).pack(side=tk.LEFT, padx=2)
        ttk.Button(toolbar, text="保存", command=self.save_file).pack(side=tk.LEFT, padx=2)
        ttk.Button(toolbar, text="另存为", command=self.save_as).pack(side=tk.LEFT, padx=2)

        self.lbl_file = ttk.Label(toolbar, text="未选择文件", foreground="gray")
        self.lbl_file.pack(side=tk.LEFT, padx=10)
        self.lbl_status = ttk.Label(toolbar, text="就绪", foreground="green")
        self.lbl_status.pack(side=tk.RIGHT, padx=5)

        if HAS_DND:
            drop_label = ttk.Label(toolbar, text="[拖拽JSON文件到此处]", foreground="blue")
            drop_label.pack(side=tk.RIGHT, padx=10)

        # Main content: Tree + Preview
        main_frame = ttk.Frame(self.root)
        main_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)

        paned = ttk.PanedWindow(main_frame, orient=tk.HORIZONTAL)
        paned.pack(fill=tk.BOTH, expand=True)

        self.json_viewer = JSONTreeViewer(self, data_changed_callback=self.on_data_changed)
        tree_frame = self.json_viewer.build(paned)
        paned.add(tree_frame, weight=3)

        # Right side: preview + quick edit
        right_frame = ttk.Frame(paned)
        paned.add(right_frame, weight=1)

        # Quick stats
        stats_frame = ttk.LabelFrame(right_frame, text="配置概览", padding=5)
        stats_frame.pack(fill=tk.X, pady=2)
        self.stats_text = tk.Text(stats_frame, height=10, width=40, font=("Consolas", 9), state=tk.DISABLED)
        self.stats_text.pack(fill=tk.X)

        # Quick attribute edit
        attr_frame = ttk.LabelFrame(right_frame, text="快速属性编辑", padding=5)
        attr_frame.pack(fill=tk.X, pady=2)

        self._quick_fields = {}
        row = 0
        for attr_key in ["impact", "armor_negation", "max_strikes", "chasing_speed", "scale"]:
            ttk.Label(attr_frame, text=attr_key).grid(row=row, column=0, sticky=tk.W, pady=1)
            var = tk.StringVar()
            entry = ttk.Entry(attr_frame, textvariable=var, width=15)
            entry.grid(row=row, column=1, padx=2, pady=1)
            var.trace_add("write", lambda *a, k=attr_key: self._quick_attr_changed(k))
            self._quick_fields[attr_key] = var
            row += 1

        ttk.Label(attr_frame, text="faction").grid(row=row, column=0, sticky=tk.W, pady=1)
        self._faction_var = tk.StringVar()
        faction_combo = ttk.Combobox(attr_frame, textvariable=self._faction_var,
                                     values=FACTION_OPTIONS, width=12)
        faction_combo.grid(row=row, column=1, padx=2, pady=1)
        self._faction_var.trace_add("write", lambda *a: self._quick_faction_changed())
        row += 1

        ttk.Label(attr_frame, text="renderer").grid(row=row, column=0, sticky=tk.W, pady=1)
        self._renderer_var = tk.StringVar()
        renderer_combo = ttk.Combobox(attr_frame, textvariable=self._renderer_var,
                                      values=RENDERER_OPTIONS, width=15)
        renderer_combo.grid(row=row, column=1, padx=2, pady=1)
        self._renderer_var.trace_add("write", lambda *a: self._quick_renderer_changed())

        # Bottom: JSON preview
        preview_frame = ttk.LabelFrame(self.root, text="JSON 预览", padding=3)
        preview_frame.pack(fill=tk.BOTH, padx=5, pady=(0, 5))

        preview_bar = ttk.Frame(preview_frame)
        preview_bar.pack(fill=tk.X)

        self.preview_text = tk.Text(preview_frame, height=8, font=("Consolas", 9), state=tk.DISABLED,
                                    wrap=tk.NONE, bg="#f5f5f5")
        preview_scroll_y = ttk.Scrollbar(preview_frame, orient=tk.VERTICAL, command=self.preview_text.yview)
        preview_scroll_x = ttk.Scrollbar(preview_frame, orient=tk.HORIZONTAL, command=self.preview_text.xview)
        self.preview_text.configure(yscrollcommand=preview_scroll_y.set, xscrollcommand=preview_scroll_x.set)
        preview_scroll_y.pack(side=tk.RIGHT, fill=tk.Y)
        preview_scroll_x.pack(side=tk.BOTTOM, fill=tk.X)
        self.preview_text.pack(fill=tk.BOTH, expand=True)

    def _setup_drag_drop(self):
        if HAS_DND:
            self.root.drop_target_register(DND_FILES)
            self.root.dnd_bind('<<Drop>>', self.on_drop)

    def on_drop(self, event):
        raw = event.data
        if raw.startswith('{') and raw.endswith('}'):
            raw = raw[1:-1]
        files = self.root.tk.splitlist(raw)
        if not files:
            return
        path = Path(files[0])
        if path.suffix.lower() not in (".json",):
            messagebox.showwarning("警告", "仅支持JSON文件")
            return
        self.load_file(path)

    def load_file(self, path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.current_file = path
            self.data = data
            self.modified = False
            self.load_data(self.data)
            self.lbl_file.config(text=path.name)
            self.lbl_status.config(text="已加载", foreground="green")
        except Exception as e:
            messagebox.showerror("错误", f"加载失败: {e}")

    def load_data(self, data):
        self.json_viewer.load_data(data)
        self._update_quick_fields(data)
        self._update_stats(data)
        self._update_preview(data)

    def _update_quick_fields(self, data):
        attrs = data.get("attributes", {})
        for k, var in self._quick_fields.items():
            var.set(str(attrs.get(k, "")))
        self._faction_var.set(data.get("faction", ""))
        self._renderer_var.set(data.get("renderer", ""))

    def _update_stats(self, data):
        lines = []
        lines.append(f"模型: {data.get('model', '?')}")
        lines.append(f"骨架: {data.get('armature', '?')}")
        lines.append(f"渲染: {data.get('renderer', '?')}")
        lines.append(f"阵营: {data.get('faction', '?')}")
        lines.append(f"人形: {data.get('isHumanoid', '?')}")
        attrs = data.get("attributes", {})
        lines.append("--- 属性 ---")
        for k in ["impact", "armor_negation", "max_strikes", "chasing_speed", "scale"]:
            lines.append(f"  {k}: {attrs.get(k, '?')}")
        cbs = data.get("combat_behavior", [])
        lines.append(f"--- 战斗行为 ({len(cbs)} 项) ---")
        for cb in cbs[:5]:
            cats = ",".join(cb.get("weapon_categories", []))
            style = cb.get("style", "?")
            series_count = len(cb.get("behavior_series", []))
            lines.append(f"  {cats} ({style}) - {series_count} 系列")
        if len(cbs) > 5:
            lines.append(f"  ... 还有 {len(cbs)-5} 项")
        self.stats_text.config(state=tk.NORMAL)
        self.stats_text.delete(1.0, tk.END)
        self.stats_text.insert(1.0, "\n".join(lines))
        self.stats_text.config(state=tk.DISABLED)

    def _update_preview(self, data):
        text = json.dumps(data, indent=4, ensure_ascii=False)
        self.preview_text.config(state=tk.NORMAL)
        self.preview_text.delete(1.0, tk.END)
        self.preview_text.insert(1.0, text)
        self.preview_text.config(state=tk.DISABLED)

    def _quick_attr_changed(self, key):
        try:
            var = self._quick_fields[key]
            val = var.get().strip()
            if val:
                if "." in val:
                    self.data.setdefault("attributes", {})[key] = float(val)
                else:
                    self.data.setdefault("attributes", {})[key] = int(val)
                self.modified = True
                self.load_data(self.data)
                self.lbl_status.config(text="已修改", foreground="orange")
        except ValueError:
            pass

    def _quick_faction_changed(self):
        val = self._faction_var.get().strip()
        if val:
            self.data["faction"] = val
            self.modified = True
            self.load_data(self.data)

    def _quick_renderer_changed(self):
        val = self._renderer_var.get().strip()
        if val:
            self.data["renderer"] = val
            self.data["model"] = "epicfight:entity/biped"
            self.data["armature"] = "epicfight:entity/biped"
            if val == "epicfight:custom":
                self.data["model"] = "epicfight:entity/biped_slim_arm"
                self.data["armature"] = "epicfight:entity/biped_slim_arm"
            self.modified = True
            self.load_data(self.data)

    def on_data_changed(self):
        self.modified = True
        self._update_stats(self.data)
        self._update_preview(self.data)
        self.lbl_status.config(text="已修改", foreground="orange")

    def open_file(self):
        path = filedialog.askopenfilename(
            title="选择ADV NPC JSON配置",
            filetypes=[("JSON文件", "*.json"), ("所有文件", "*.*")]
        )
        if path:
            self.load_file(Path(path))

    def new_file(self):
        if self.modified:
            if not messagebox.askyesno("确认", "当前有未保存的修改，放弃?"):
                return
        self.current_file = None
        self.data = deepcopy(TEMPLATE)
        self.modified = False
        self.load_data(self.data)
        self.lbl_file.config(text="新建配置")
        self.lbl_status.config(text="就绪", foreground="green")

    def save_file(self):
        if self.current_file:
            self._write_json(self.current_file)
        else:
            self.save_as()

    def save_as(self):
        path = filedialog.asksaveasfilename(
            title="保存JSON配置",
            defaultextension=".json",
            filetypes=[("JSON文件", "*.json"), ("所有文件", "*.*")],
            initialdir=str(Path(__file__).parent / "data" / "customnpcs" / "adv_npc_epicfight_mobpatch"
                           if (Path(__file__).parent / "data").exists() else ".")
        )
        if path:
            self._write_json(Path(path))

    def _write_json(self, path):
        try:
            # Ensure data has all required top-level keys
            for key in TEMPLATE:
                if key not in self.data:
                    self.data[key] = deepcopy(TEMPLATE[key])
            with open(path, "w", encoding="utf-8") as f:
                json.dump(self.data, f, indent=4, ensure_ascii=False)
            self.current_file = path
            self.modified = False
            self.lbl_file.config(text=path.name)
            self.lbl_status.config(text="已保存", foreground="green")
            messagebox.showinfo("成功", f"已保存到:\n{path}")
        except Exception as e:
            messagebox.showerror("错误", f"保存失败: {e}")


def main():
    if HAS_DND:
        root = TkinterDnD.Tk()
    else:
        root = tk.Tk()
        print("提示: 安装 tkinterdnd2 可获得拖拽支持: pip install tkinterdnd2")
    app = EditorApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
