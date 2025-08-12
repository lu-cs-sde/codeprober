-- Full disclosure: This file is 100% vibe coded.
-- This file is used to perform custom processing of the markdown files as they are
-- being converted to HTML. Full disclosure: the file is 100% "vibe coded" as I did
-- not feel like learning LUA and the Pandoc API, and generated HTML files are not
-- critical to be 100% correct.

-- Global variables to track document state
local is_features_doc = false

function Link(el)
  el.target = string.gsub(el.target, "%.md", ".html")
  return el
end


-- Process documents to wrap feature sections
function Pandoc(doc)
  -- Detect if this is features.md by looking for characteristic content
  local has_features_title = false
  local has_feature_headings = false

  for _, block in ipairs(doc.blocks) do
    if block.t == "Header" and block.level == 1 then
      local title = pandoc.utils.stringify(block)
      if title == "Features" then
        has_features_title = true
      end
    elseif block.t == "Header" and block.level == 3 then
      local title = pandoc.utils.stringify(block)
      if title:match("Creating Probe") or title:match("AST View") or title:match("Pretty Print") then
        has_feature_headings = true
      end
    end
  end

  is_features_doc = has_features_title and has_feature_headings

  if not is_features_doc then
    return doc
  end

  local new_blocks = {}
  local current_feature_blocks = {}
  local in_feature = false

  for i, block in ipairs(doc.blocks) do
    if block.t == "Header" and block.level == 3 then
      -- If we were in a feature, wrap the previous feature content
      if in_feature then
        local text_content = {}
        local feature_image = nil

        -- Separate image from text content (skip header which is at index 1)
        for i = 2, #current_feature_blocks do
          local content_block = current_feature_blocks[i]
          if content_block.t == "Para" and content_block.content[1] and content_block.content[1].t == "Image" then
            -- Check if this paragraph has content beyond just the image
            if #content_block.content > 1 then
              -- Create separate image and text blocks
              feature_image = content_block.content[1] -- Just the image element, not wrapped in Para
              -- Create text content from the rest
              local text_content_inline = {}
              for j = 2, #content_block.content do
                table.insert(text_content_inline, content_block.content[j])
              end
              if #text_content_inline > 0 then
                table.insert(text_content, pandoc.Para(text_content_inline))
              end
            else
              -- Only an image, extract it from the paragraph
              feature_image = content_block.content[1] -- Just the image element
            end
          else
            table.insert(text_content, content_block)
          end
        end

        -- Create the feature wrapper div
        local feature_div_content = {}

        -- Add the header first
        local feature_header = current_feature_blocks[1] -- The header should be the first block
        table.insert(feature_div_content, feature_header)

        -- Add the image if exists
        if feature_image then
          table.insert(feature_div_content, feature_image)
        end

        -- Wrap text content in a text div if there's any text content
        if #text_content > 0 then
          local text_div = pandoc.Div(text_content, pandoc.Attr("", {"feature-description"}))
          table.insert(feature_div_content, text_div)
        end

        -- Create the main feature wrapper
        local feature_div = pandoc.Div(feature_div_content, pandoc.Attr("", {"feature-wrapper"}))
        table.insert(new_blocks, feature_div)
      end

      -- Start new feature
      current_feature_blocks = {block}
      in_feature = true
    elseif in_feature and block.t == "Header" then
      -- If we encounter any other header while in a feature, stop the feature and add the header normally
      -- First wrap the current feature
      local text_content = {}
      local feature_image = nil

      -- Separate image from text content (skip header which is at index 1)
      for i = 2, #current_feature_blocks do
        local content_block = current_feature_blocks[i]
        if content_block.t == "Para" and content_block.content[1] and content_block.content[1].t == "Image" then
          -- Check if this paragraph has content beyond just the image
          if #content_block.content > 1 then
            -- Create separate image and text blocks
            feature_image = content_block.content[1] -- Just the image element, not wrapped in Para
            -- Create text content from the rest
            local text_content_inline = {}
            for j = 2, #content_block.content do
              table.insert(text_content_inline, content_block.content[j])
            end
            if #text_content_inline > 0 then
              table.insert(text_content, pandoc.Para(text_content_inline))
            end
          else
            -- Only an image, extract it from the paragraph
            feature_image = content_block.content[1] -- Just the image element
          end
        else
          table.insert(text_content, content_block)
        end
      end

      -- Create the feature wrapper div
      local feature_div_content = {}

      -- Add the header first
      local feature_header = current_feature_blocks[1] -- The header should be the first block
      table.insert(feature_div_content, feature_header)

      -- Add the image if exists
      if feature_image then
        table.insert(feature_div_content, feature_image)
      end

      -- Wrap text content in a text div if there's any text content
      if #text_content > 0 then
        local text_div = pandoc.Div(text_content, pandoc.Attr("", {"feature-description"}))
        table.insert(feature_div_content, text_div)
      end

      -- Create the main feature wrapper
      local feature_div = pandoc.Div(feature_div_content, pandoc.Attr("", {"feature-wrapper"}))
      table.insert(new_blocks, feature_div)

      -- End feature mode and add the header normally
      in_feature = false
      table.insert(new_blocks, block)
    elseif in_feature then
      -- Collect blocks for current feature
      table.insert(current_feature_blocks, block)
    else
      -- Not in a feature section, add block as is
      table.insert(new_blocks, block)
    end
  end

  -- Handle the last feature if exists
  if in_feature then
    local text_content = {}
    local feature_image = nil

    -- Separate image from text content (skip header which is at index 1)
    for i = 2, #current_feature_blocks do
      local content_block = current_feature_blocks[i]
      if content_block.t == "Para" and content_block.content[1] and content_block.content[1].t == "Image" then
        -- Check if this paragraph has content beyond just the image
        if #content_block.content > 1 then
          -- Create separate image and text blocks
          feature_image = content_block.content[1] -- Just the image element, not wrapped in Para
          -- Create text content from the rest
          local text_content_inline = {}
          for j = 2, #content_block.content do
            table.insert(text_content_inline, content_block.content[j])
          end
          if #text_content_inline > 0 then
            table.insert(text_content, pandoc.Para(text_content_inline))
          end
        else
          -- Only an image, extract it from the paragraph
          feature_image = content_block.content[1] -- Just the image element
        end
      else
        table.insert(text_content, content_block)
      end
    end

    -- Create the feature wrapper div
    local feature_div_content = {}

    -- Add the header first
    local feature_header = current_feature_blocks[1] -- The header should be the first block
    table.insert(feature_div_content, feature_header)

    -- Add the image if exists
    if feature_image then
      table.insert(feature_div_content, feature_image)
    end

    -- Wrap text content in a text div if there's any text content
    if #text_content > 0 then
      local text_div = pandoc.Div(text_content, pandoc.Attr("", {"feature-description"}))
      table.insert(feature_div_content, text_div)
    end

    -- Create the main feature wrapper
    local feature_div = pandoc.Div(feature_div_content, pandoc.Attr("", {"feature-wrapper"}))
    table.insert(new_blocks, feature_div)
  end

  return pandoc.Pandoc(new_blocks, doc.meta)
end

-- table-data-label.lua
function Table(el)
  -- Get header texts from the first row of the TableHead
  local headers = {}

  -- Check if table has a head and extract headers
  if el.head and el.head.rows and #el.head.rows > 0 and el.head.rows[1].cells then
    for i, cell in ipairs(el.head.rows[1].cells) do
      headers[i] = pandoc.utils.stringify(cell.content)
    end
  end

  -- Process each body row
  if el.bodies then
    for _, body in ipairs(el.bodies) do
      -- The actual rows are in body.body, not body.rows
      if body.body then
        for _, row in ipairs(body.body) do
          if row.cells then
            for colIndex, cell in ipairs(row.cells) do
              -- Ensure Attr exists
              local attr = cell.attr or pandoc.Attr()
              -- Set data-label attribute to header text
              attr.attributes["data-label"] = headers[colIndex] or ""
              cell.attr = attr
            end
          end
        end
      end
    end
  end

  -- Optional: process footer rows too
  -- Footer might also use .body structure instead of .rows
  if el.foot then
    if el.foot.rows then
      for _, row in ipairs(el.foot.rows) do
        if row.cells then
          for colIndex, cell in ipairs(row.cells) do
            local attr = cell.attr or pandoc.Attr()
            attr.attributes["data-label"] = headers[colIndex] or ""
            cell.attr = attr
          end
        end
      end
    elseif el.foot.body then
      for _, row in ipairs(el.foot.body) do
        if row.cells then
          for colIndex, cell in ipairs(row.cells) do
            local attr = cell.attr or pandoc.Attr()
            attr.attributes["data-label"] = headers[colIndex] or ""
            cell.attr = attr
          end
        end
      end
    end
  end

  -- return el
  return pandoc.Div({el}, pandoc.Attr("", {"table-container"}))
end
